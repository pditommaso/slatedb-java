package com.slatedb

import com.slatedb.config.*
import com.slatedb.exceptions.*
import spock.lang.Specification
import spock.lang.TempDir
import java.nio.file.Path

/**
 * Unit tests for SlateDB using mock native implementation.
 * This allows testing the Java code structure without requiring native library compilation.
 */
class SlateDBMockSpec extends Specification {
    
    @TempDir
    Path tempDir
    
    def "should create database with local storage using mock"() {
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
    
    def "should perform basic put and get operations with mock"() {
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
    
    def "should return null for non-existent key with mock"() {
        given:
        def db = createTestDB()
        
        when:
        def result = db.get("non-existent".bytes)
        
        then:
        result == null
        
        cleanup:
        db?.close()
    }
    
    def "should handle delete operations with mock"() {
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
    
    def "should handle empty values with mock"() {
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
    
    def "should reject null or empty keys with mock"() {
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
    
    def "should handle write batch operations with mock"() {
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
    
    def "should handle flush operations with mock"() {
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
    
    def "should handle database closure with mock"() {
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
    
    def "should handle multiple close calls with mock"() {
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