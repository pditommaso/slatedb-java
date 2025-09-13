package com.slatedb

import java.util.concurrent.TimeUnit

import com.slatedb.config.AWSConfig
import com.slatedb.config.SlateDBOptions
import com.slatedb.config.StoreConfig
import com.slatedb.config.Provider
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Timeout
// SlateDB imports

/**
 * End-to-end integration tests for SlateDB with AWS S3 backend.
 * These tests require AWS credentials and S3 access.
 * 
 * Run with: ./gradlew e2eTest
 * 
 * Configuration via system properties:
 * - slatedb.test.s3.bucket (default: slatedb-sdk-dev)
 * - slatedb.test.aws.region (default: us-east-1)
 * - slatedb.test.aws.accessKey
 * - slatedb.test.aws.secretKey
 * 
 * Or environment variables:
 * - AWS_ACCESS_KEY_ID
 * - AWS_SECRET_ACCESS_KEY
 */
@Tag("e2e")
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class E2ESlateDBSpec extends Specification {

    @Shared
    String s3Bucket
    
    @Shared
    String awsRegion
    
    @Shared
    String testKeyPrefix
    
    @Shared
    StoreConfig storeConfig
    
    def setupSpec() {
        s3Bucket = System.getProperty('slatedb.test.s3.bucket', 'slatedb-sdk-dev')
        awsRegion = System.getProperty('slatedb.test.aws.region', 'us-east-1')
        testKeyPrefix = "e2e-test-${System.currentTimeMillis()}"
        
        // Get AWS credentials from system properties or environment
        String accessKey = System.getProperty('slatedb.test.aws.accessKey') ?: System.getenv('AWS_ACCESS_KEY_ID')
        String secretKey = System.getProperty('slatedb.test.aws.secretKey') ?: System.getenv('AWS_SECRET_ACCESS_KEY')
        
        // Create AWS configuration
        def awsConfigBuilder = AWSConfig.builder()
            .bucket(s3Bucket)
            .region(awsRegion)
            
        if (accessKey && secretKey) {
            awsConfigBuilder.accessKey(accessKey).secretKey(secretKey)
        }
        
        def awsConfig = awsConfigBuilder.build()
        
        // Create S3 store configuration
        storeConfig = StoreConfig.builder()
            .provider(Provider.AWS)
            .aws(awsConfig)
            .build()
            
        println "E2E Test Configuration:"
        println "  S3 Bucket: ${s3Bucket}"
        println "  AWS Region: ${awsRegion}"
        println "  Key Prefix: ${testKeyPrefix}"
    }
    
    def cleanupSpec() {
        // Note: In a real scenario, you might want to clean up test data
        // For now, we rely on the unique key prefix per test run
        println "E2E tests completed. Test data in S3 under prefix: ${testKeyPrefix}"
    }

    def "should open and close SlateDB with local backend"() {
        given: "SlateDB options for local backend"
        def localStoreConfig = StoreConfig.local()
        def options = SlateDBOptions.builder()
            .flushInterval(java.time.Duration.ofMillis(1000))
            .build()
            
        when: "opening SlateDB with local configuration"
        def db = SlateDB.open("/tmp/slatedb-e2e-local-${System.currentTimeMillis()}", localStoreConfig, options)
        
        then: "database should be successfully opened"
        db != null
        
        cleanup:
        db?.close()
    }

    @Requires({ hasAwsCredentials() })
    def "should open and close SlateDB with S3 backend"() {
        given: "SlateDB options for S3 backend"
        def options = SlateDBOptions.builder()
            .flushInterval(java.time.Duration.ofMillis(1000))
            .build()
            
        when: "opening SlateDB with S3 configuration"
        def db = SlateDB.open("/tmp/slatedb-e2e-${System.currentTimeMillis()}", storeConfig, options)
        
        then: "database should be successfully opened"
        db != null
        
        cleanup:
        db?.close()
    }

    @Requires({ hasAwsCredentials() })
    def "should perform basic CRUD operations with S3 backend"() {
        given: "an opened SlateDB instance with S3 backend"
        def options = SlateDBOptions.builder().build()
        def db = SlateDB.open("/tmp/slatedb-e2e-crud-${System.currentTimeMillis()}", storeConfig, options)
        
        when: "putting a key-value pair"
        def key1 = "test-key-1".getBytes()
        def value1 = "test-value-1".getBytes()
        db.put(key1, value1)
        
        then: "the value should be retrievable"
        def retrievedValue = db.get(key1)
        Arrays.equals(retrievedValue, value1)
        
        when: "updating the value"
        def updatedValue1 = "updated-value-1".getBytes()
        db.put(key1, updatedValue1)
        
        then: "the updated value should be retrievable"
        def newRetrievedValue = db.get(key1)
        Arrays.equals(newRetrievedValue, updatedValue1)
        
        when: "deleting the key"
        db.delete(key1)
        
        then: "the key should no longer exist"
        def deletedValue = db.get(key1)
        deletedValue == null
        
        cleanup:
        db?.close()
    }

    @Requires({ hasAwsCredentials() })
    def "should handle write batches with S3 backend"() {
        given: "an opened SlateDB instance with S3 backend"
        def options = SlateDBOptions.builder().build()
        def db = SlateDB.open("/tmp/slatedb-e2e-batch-${System.currentTimeMillis()}", storeConfig, options)
        
        and: "a write batch with multiple operations"
        def batch = WriteBatch.create()
        def key1 = "batch-key-1".getBytes()
        def key2 = "batch-key-2".getBytes()
        def key3 = "batch-key-3".getBytes()
        def value1 = "batch-value-1".getBytes()
        def value2 = "batch-value-2".getBytes()
        def value3 = "batch-value-3".getBytes()
        
        batch.put(key1, value1)
        batch.put(key2, value2)
        batch.put(key3, value3)
        batch.delete(key2)  // Delete one of them
        
        when: "applying the batch"
        db.apply(batch)
        
        then: "the batch operations should be applied correctly"
        Arrays.equals(db.get(key1), value1)
        db.get(key2) == null  // Should be deleted
        Arrays.equals(db.get(key3), value3)
        
        cleanup:
        batch?.close()
        db?.close()
    }

    @Requires({ hasAwsCredentials() })
    def "should iterate over keys with S3 backend"() {
        given: "an opened SlateDB instance with S3 backend"
        def options = SlateDBOptions.builder().build()
        def db = SlateDB.open("/tmp/slatedb-e2e-iter-${System.currentTimeMillis()}", storeConfig, options)
        
        and: "multiple key-value pairs"
        def testData = [
            "iter-key-a": "value-a",
            "iter-key-b": "value-b", 
            "iter-key-c": "value-c",
            "iter-key-d": "value-d"
        ]
        
        testData.each { k, v ->
            db.put(k.getBytes(), v.getBytes())
        }
        
        when: "creating an iterator"
        def iterator = db.iterator()
        def results = []
        
        while (iterator.hasNext()) {
            def kv = iterator.next()
            results.add([
                key: new String(kv.key()), 
                value: new String(kv.value())
            ])
        }
        
        then: "all key-value pairs should be iterated"
        results.size() >= 4  // At least our 4 pairs (may include others from previous tests)
        def ourResults = results.findAll { it.key.startsWith("iter-key-") }
        ourResults.size() == 4
        ourResults.find { it.key == "iter-key-a" && it.value == "value-a" }
        ourResults.find { it.key == "iter-key-b" && it.value == "value-b" }
        ourResults.find { it.key == "iter-key-c" && it.value == "value-c" }
        ourResults.find { it.key == "iter-key-d" && it.value == "value-d" }
        
        cleanup:
        iterator?.close()
        db?.close()
    }

    @Requires({ hasAwsCredentials() })
    def "should handle large values with S3 backend"() {
        given: "an opened SlateDB instance with S3 backend"
        def options = SlateDBOptions.builder().build()
        def db = SlateDB.open("/tmp/slatedb-e2e-large-${System.currentTimeMillis()}", storeConfig, options)
        
        and: "a large value (1MB)"
        def largeValue = ("x" * (1024 * 1024)).getBytes()  // 1MB byte array
        def largeKey = "large-key".getBytes()
        
        when: "storing the large value"
        db.put(largeKey, largeValue)
        
        then: "the large value should be retrievable"
        def retrievedValue = db.get(largeKey)
        Arrays.equals(retrievedValue, largeValue)
        retrievedValue.length == 1024 * 1024
        
        cleanup:
        db?.close()
    }

    @Requires({ hasAwsCredentials() })
    def "should handle concurrent operations with S3 backend"() {
        given: "an opened SlateDB instance with S3 backend"
        def options = SlateDBOptions.builder()
            .flushInterval(java.time.Duration.ofMillis(500))  // More frequent flushes for concurrency test
            .build()
        def db = SlateDB.open("/tmp/slatedb-e2e-concurrent-${System.currentTimeMillis()}", storeConfig, options)
        
        when: "performing concurrent operations"
        def threads = []
        def numThreads = 5
        def operationsPerThread = 10
        
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i
            threads.add(Thread.start {
                for (int j = 0; j < operationsPerThread; j++) {
                    def key = "concurrent-key-${threadId}-${j}".getBytes()
                    def value = "concurrent-value-${threadId}-${j}".getBytes()
                    db.put(key, value)
                }
            })
        }
        
        // Wait for all threads to complete
        threads.each { it.join() }
        
        then: "all values should be present"
        def allPresent = true
        for (int i = 0; i < numThreads; i++) {
            for (int j = 0; j < operationsPerThread; j++) {
                def key = "concurrent-key-${i}-${j}".getBytes()
                def expectedValue = "concurrent-value-${i}-${j}".getBytes()
                def actualValue = db.get(key)
                if (!Arrays.equals(actualValue, expectedValue)) {
                    allPresent = false
                    println "Missing or incorrect value for key: concurrent-key-${i}-${j}, " +
                           "expected: ${new String(expectedValue)}, " +
                           "actual: ${actualValue ? new String(actualValue) : 'null'}"
                    break
                }
            }
            if (!allPresent) break
        }
        
        allPresent
        
        cleanup:
        db?.close()
    }

    @Requires({ hasAwsCredentials() })
    def "should persist data across database reopenings with S3 backend"() {
        given: "a database path and test data"
        def dbPath = "/tmp/slatedb-e2e-persist-${System.currentTimeMillis()}"
        def options = SlateDBOptions.builder().build()
        def testKey = "persist-key".getBytes()
        def testValue = "persist-value".getBytes()
        
        when: "storing data and closing database"
        def db1 = SlateDB.open(dbPath, storeConfig, options)
        db1.put(testKey, testValue)
        db1.close()
        
        and: "reopening the database"
        def db2 = SlateDB.open(dbPath, storeConfig, options)
        
        then: "the data should still be present"
        def retrievedValue = db2.get(testKey)
        Arrays.equals(retrievedValue, testValue)
        
        cleanup:
        db2?.close()
    }

    private static boolean hasAwsCredentials() {
        def hasSystemProperty = System.getProperty('slatedb.test.aws.accessKey') != null
        def hasEnvironmentVar = System.getenv('AWS_ACCESS_KEY_ID') != null
        return hasSystemProperty || hasEnvironmentVar
    }
}
