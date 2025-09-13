package com.slatedb

import com.slatedb.config.*
import com.slatedb.exceptions.*
import spock.lang.Specification
import spock.lang.TempDir
import java.nio.file.Path

/**
 * Comprehensive unit tests for SlateDB using Spock framework.
 * 
 * These tests verify the core functionality of the SlateDB Java client
 * including CRUD operations, batch operations, iterators, and configuration.
 */
class SlateDBSpec extends Specification {
    
    @TempDir
    Path tempDir
    
    def "should create database with local storage"() {
        given:
        def storeConfig = StoreConfig.local()
        def dbPath = tempDir.resolve("test-db").toString()
        
        when:
        def db = SlateDBTest.open(dbPath, storeConfig, null)
        
        then:
        db != null
        !db.isClosed()
        
        cleanup:
        db?.close()
    }
    
    def "should perform basic put and get operations"() {
        given:
        def db = createTestDB()
        def key = "test-key".bytes
        def value = "test-value".bytes
        
        when:
        db.put(key, value)
        def result = db.get(key)
        
        then:
        result == value
        
        cleanup:
        db?.close()
    }
    
    def "should return null for non-existent key"() {
        given:
        def db = createTestDB()
        
        when:
        def result = db.get("non-existent".bytes)
        
        then:
        result == null
        
        cleanup:
        db?.close()
    }
    
    def "should handle delete operations"() {
        given:
        def db = createTestDB()
        def key = "delete-me".bytes
        def value = "some-value".bytes
        
        when:
        db.put(key, value)
        def beforeDelete = db.get(key)
        db.delete(key)
        def afterDelete = db.get(key)
        
        then:
        beforeDelete == value
        afterDelete == null
        
        cleanup:
        db?.close()
    }
    
    def "should handle empty values"() {
        given:
        def db = createTestDB()
        def key = "empty-value-key".bytes
        def emptyValue = new byte[0]
        
        when:
        db.put(key, emptyValue)
        def result = db.get(key)
        
        then:
        result == emptyValue
        
        cleanup:
        db?.close()
    }
    
    def "should reject null or empty keys"() {
        given:
        def db = createTestDB()
        
        when:
        db.put(invalidKey, "value".bytes)
        
        then:
        thrown(SlateDBInvalidArgumentException)
        
        cleanup:
        db?.close()
        
        where:
        invalidKey << [null, new byte[0]]
    }
    
    def "should handle write batch operations"() {
        given:
        def db = createTestDB()
        def batch = new WriteBatchTest()
        
        when:
        batch.put("key1".bytes, "value1".bytes)
        batch.put("key2".bytes, "value2".bytes)
        batch.delete("key3".bytes) // Non-existent key - should be safe
        db.write(batch)
        
        then:
        db.get("key1".bytes) == "value1".bytes
        db.get("key2".bytes) == "value2".bytes
        db.get("key3".bytes) == null
        
        cleanup:
        batch?.close()
        db?.close()
    }
    
    def "should prevent batch reuse after write"() {
        given:
        def db = createTestDB()
        def batch = new WriteBatchTest()
        batch.put("key1".bytes, "value1".bytes)
        db.write(batch)
        
        when:
        batch.put("key2".bytes, "value2".bytes)
        
        then:
        thrown(SlateDBException)
        
        cleanup:
        batch?.close()
        db?.close()
    }
    
    def "should handle write options"() {
        given:
        def db = createTestDB()
        def key = "write-options-key".bytes
        def value = "write-options-value".bytes
        def writeOptions = WriteOptions.fastWrite() // Non-durable write
        
        when:
        db.put(key, value, null, writeOptions)
        def result = db.get(key)
        
        then:
        result == value
        
        cleanup:
        db?.close()
    }
    
    def "should handle put options with TTL"() {
        given:
        def db = createTestDB()
        def key = "ttl-key".bytes
        def value = "ttl-value".bytes
        def putOptions = PutOptions.noExpiry()
        
        when:
        db.put(key, value, putOptions, null)
        def result = db.get(key)
        
        then:
        result == value
        
        cleanup:
        db?.close()
    }
    
    def "should handle read options"() {
        given:
        def db = createTestDB()
        def key = "read-options-key".bytes
        def value = "read-options-value".bytes
        def readOptions = ReadOptions.eventualRead()
        
        when:
        db.put(key, value)
        def result = db.get(key, readOptions)
        
        then:
        result == value
        
        cleanup:
        db?.close()
    }
    
    def "should create iterator for scanning"() {
        given:
        def db = createTestDB()
        
        // Put some test data
        db.put("prefix:key1".bytes, "value1".bytes)
        db.put("prefix:key2".bytes, "value2".bytes)
        db.put("prefix:key3".bytes, "value3".bytes)
        db.put("other:key1".bytes, "other-value".bytes)
        
        when:
        def iterator = db.scan("prefix:".bytes, "prefix;".bytes) // Scan prefix range
        def results = []
        
        // Note: In a real implementation, we'd use hasNext() and next()
        // For this test, we're just verifying iterator creation
        
        then:
        iterator != null
        !iterator.isClosed()
        
        cleanup:
        iterator?.close()
        db?.close()
    }
    
    def "should handle flush operations"() {
        given:
        def db = createTestDB()
        
        when:
        db.put("flush-test".bytes, "flush-value".bytes)
        db.flush()
        def result = db.get("flush-test".bytes)
        
        then:
        result == "flush-value".bytes
        
        cleanup:
        db?.close()
    }
    
    def "should handle database closure"() {
        given:
        def db = createTestDB()
        
        when:
        db.close()
        
        then:
        db.isClosed()
        
        when:
        db.get("any-key".bytes)
        
        then:
        thrown(SlateDBException)
    }
    
    def "should handle multiple close calls"() {
        given:
        def db = createTestDB()
        
        when:
        db.close()
        db.close() // Second close should be safe
        
        then:
        db.isClosed()
        noExceptionThrown()
    }
    
    def "should validate configuration objects"() {
        given:
        def awsConfig = AWSConfig.builder()
                .bucket("test-bucket")
                .region("us-east-1")
                .build()
        def storeConfig = StoreConfig.aws(awsConfig)
        
        when:
        def result = storeConfig.getProvider()
        
        then:
        result == Provider.AWS
        storeConfig.getAws() == awsConfig
    }
    
    def "should create SlateDB options with builder pattern"() {
        given:
        def options = SlateDBOptions.builder()
                .l0SstSizeBytes(64L * 1024 * 1024) // 64MB
                .flushInterval(java.time.Duration.ofMillis(100))
                .cacheFolder("/tmp/cache")
                .sstBlockSize(SstBlockSize.SIZE_8_KIB)
                .build()
        
        expect:
        options.getL0SstSizeBytes() == 64L * 1024 * 1024
        options.getFlushInterval() == java.time.Duration.ofMillis(100)
        options.getCacheFolder() == "/tmp/cache"
        options.getSstBlockSize() == SstBlockSize.SIZE_8_KIB
    }
    
    private SlateDBTest createTestDB() {
        def storeConfig = StoreConfig.local()
        def dbPath = tempDir.resolve("test-db-${UUID.randomUUID()}").toString()
        return SlateDBTest.open(dbPath, storeConfig, null)
    }
}