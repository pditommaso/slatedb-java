# SlateDB Java Client

A high-performance Java client for [SlateDB](https://slatedb.io/), a cloud-native embedded storage engine built for modern applications. This client provides a type-safe, idiomatic Java API using Java 24's Foreign Function Interface (FFI) to integrate with SlateDB's native Go bindings.

## Features

- **High Performance**: Direct integration with native SlateDB using Java FFI
- **Cloud-Native**: Built-in support for AWS S3 and local storage backends
- **ACID Transactions**: Full transactional guarantees with write batching
- **Concurrent Operations**: Thread-safe operations with configurable concurrency
- **Modern Java**: Leverages Java 24+ features for optimal performance
- **Comprehensive API**: Complete coverage of SlateDB operations

## Requirements

- **Java 24+** (required for FFI support)
- **Gradle 8.0+**
- **AWS credentials** (for S3 backend usage)

## Installation

Add the dependency to your `build.gradle`:

```gradle
dependencies {
    implementation 'com.slatedb:slatedb-java:0.1.0'
}
```

## Quick Start

### Local Storage Example

```java
import com.slatedb.*;
import com.slatedb.config.*;

public class LocalExample {
    public static void main(String[] args) throws SlateDBException {
        // Configure local storage
        StoreConfig config = StoreConfig.local();
        SlateDBOptions options = SlateDBOptions.builder()
            .flushInterval(Duration.ofSeconds(5))
            .build();
        
        // Open database
        try (SlateDB db = SlateDB.open("/tmp/mydb", config, options)) {
            // Basic operations
            db.put("hello".getBytes(), "world".getBytes());
            byte[] value = db.get("hello".getBytes());
            System.out.println(new String(value)); // Output: world
            
            // Delete
            db.delete("hello".getBytes());
        }
    }
}
```

### AWS S3 Example

```java
import com.slatedb.*;
import com.slatedb.config.*;

public class S3Example {
    public static void main(String[] args) throws SlateDBException {
        // Configure S3 storage
        AWSConfig awsConfig = AWSConfig.builder()
            .bucket("my-slatedb-bucket")
            .region("us-east-1")
            .build();
        
        StoreConfig config = StoreConfig.builder()
            .provider(Provider.AWS)
            .aws(awsConfig)
            .build();
        
        SlateDBOptions options = SlateDBOptions.builder()
            .flushInterval(Duration.ofMillis(100))
            .build();
        
        // Open database with S3 backend
        try (SlateDB db = SlateDB.open("my-app-data", config, options)) {
            // Batch operations
            try (WriteBatch batch = new WriteBatch()) {
                batch.put("user:1".getBytes(), "alice".getBytes());
                batch.put("user:2".getBytes(), "bob".getBytes());
                batch.delete("old-key".getBytes());
                
                db.write(batch);
                db.flush(); // Ensure data is persisted to S3
            }
            
            // Scan operations
            try (Iterator iter = db.scan("user:".getBytes(), "user:z".getBytes())) {
                while (iter.hasNext()) {
                    KeyValue kv = iter.next();
                    System.out.println("Key: " + new String(kv.getKey()) + 
                                     ", Value: " + new String(kv.getValue()));
                }
            }
        }
    }
}
```

## API Reference

### SlateDB Core Operations

#### Database Lifecycle

```java
// Open database
SlateDB db = SlateDB.open(String path, StoreConfig config, SlateDBOptions options)

// Check if closed
boolean isClosed = db.isClosed()

// Close database (implements AutoCloseable)
db.close()
```

#### Basic Operations

```java
// Put key-value pair
db.put(byte[] key, byte[] value)
db.put(byte[] key, byte[] value, PutOptions putOptions, WriteOptions writeOptions)

// Get value by key
byte[] value = db.get(byte[] key)
byte[] value = db.get(byte[] key, ReadOptions readOptions)
// Returns null if key not found

// Delete key
db.delete(byte[] key)
db.delete(byte[] key, WriteOptions writeOptions)

// Flush writes to persistent storage
db.flush()
```

#### Batch Operations

```java
// Create and use write batch
try (WriteBatch batch = new WriteBatch()) {
    batch.put("key1".getBytes(), "value1".getBytes());
    batch.put("key2".getBytes(), "value2".getBytes(), putOptions);
    batch.delete("key3".getBytes());
    
    // Execute batch atomically
    db.write(batch);
    db.write(batch, writeOptions);
}
```

#### Scanning and Iteration

```java
// Scan key range
try (Iterator iter = db.scan(startKey, endKey)) {
    while (iter.hasNext()) {
        KeyValue kv = iter.next();
        // Process key-value pair
    }
}

// Scan with options
try (Iterator iter = db.scan(startKey, endKey, scanOptions)) {
    // Seek to specific key
    iter.seek("target-key".getBytes());
    
    if (iter.hasNext()) {
        KeyValue kv = iter.next();
    }
}
```

### Configuration

#### Store Configuration

```java
// Local storage
StoreConfig local = StoreConfig.local();

// AWS S3 storage
AWSConfig awsConfig = AWSConfig.builder()
    .bucket("bucket-name")
    .region("us-east-1")
    .accessKey("access-key")     // Optional, uses env vars if not set
    .secretKey("secret-key")     // Optional, uses env vars if not set
    .build();

StoreConfig s3 = StoreConfig.builder()
    .provider(Provider.AWS)
    .aws(awsConfig)
    .build();
```

#### Database Options

```java
SlateDBOptions options = SlateDBOptions.builder()
    .flushInterval(Duration.ofSeconds(1))           // Auto-flush interval
    .manifestPollInterval(Duration.ofSeconds(1))    // Manifest polling
    .manifestUpdateTimeout(Duration.ofMinutes(5))   // Manifest update timeout
    .minFilterKeys(1000)                            // Minimum keys for bloom filter
    .filterBitsPerKey(10)                          // Bloom filter bits per key
    .l0SstSizeBytes(64 * 1024 * 1024)             // L0 SST file size (64MB)
    .l0MaxSsts(8)                                  // Maximum L0 SST files
    .maxUnflushedBytes(1024 * 1024 * 1024)        // Max unflushed bytes (1GB)
    .build();
```

#### Operation Options

```java
// Put options
PutOptions putOpts = PutOptions.builder()
    .ttl(Duration.ofHours(24))    // Time-to-live
    .build();

// Write options
WriteOptions writeOpts = WriteOptions.builder()
    .awaitDurable(true)           // Wait for durable write
    .build();

// Read options  
ReadOptions readOpts = ReadOptions.builder()
    .durabilityFilter(DurabilityFilter.COMMITTED)  // Read only committed data
    .dirty(false)                                   // Exclude dirty reads
    .build();

// Scan options
ScanOptions scanOpts = ScanOptions.builder()
    .durabilityFilter(DurabilityFilter.COMMITTED)
    .dirty(false)
    .readAheadBytes(4 * 1024 * 1024)              // Read-ahead size (4MB)
    .cacheBlocks(true)                             // Enable block caching
    .maxFetchTasks(4)                              // Max concurrent fetch tasks
    .build();
```

### Data Types

#### KeyValue

```java
public class KeyValue {
    public byte[] getKey()
    public byte[] getValue()
}
```

#### Exception Handling

```java
try {
    db.put(key, value);
} catch (SlateDBNotFoundException e) {
    // Key not found
} catch (SlateDBInvalidArgumentException e) {
    // Invalid argument (null/empty key, etc.)
} catch (SlateDBException e) {
    // General SlateDB error
}
```

## Configuration

### Environment Variables

For AWS S3 backend, you can use standard AWS environment variables:

```bash
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
export AWS_DEFAULT_REGION=us-east-1
```

### JVM Options

For optimal performance with Java FFI:

```bash
java --enable-native-access=ALL-UNNAMED -XX:+UnlockExperimentalVMOptions your-app
```

## Best Practices

### Resource Management

Always use try-with-resources for proper cleanup:

```java
try (SlateDB db = SlateDB.open(path, config, options);
     WriteBatch batch = new WriteBatch();
     Iterator iter = db.scan(start, end)) {
    // Use resources
} // Automatic cleanup
```

### Error Handling

```java
try {
    db.put(key, value);
    db.flush(); // Ensure persistence for S3 backend
} catch (SlateDBException e) {
    logger.error("Database operation failed", e);
    // Handle error appropriately
}
```

### Performance Tips

1. **Batching**: Use `WriteBatch` for multiple operations
2. **Flushing**: Call `flush()` after writes when using S3 backend
3. **Connection Pooling**: Reuse database instances when possible
4. **Memory Management**: Close iterators and batches promptly

### Thread Safety

SlateDB is thread-safe for concurrent reads and writes:

```java
// Multiple threads can safely access the same database instance
ExecutorService executor = Executors.newFixedThreadPool(10);

for (int i = 0; i < 100; i++) {
    final int id = i;
    executor.submit(() -> {
        try {
            db.put(("key-" + id).getBytes(), ("value-" + id).getBytes());
        } catch (SlateDBException e) {
            logger.error("Write failed", e);
        }
    });
}
```

## Building from Source

```bash
git clone https://github.com/slatedb/slatedb-java.git
cd slatedb-java
./gradlew build
```

### Running Tests

```bash
# Unit tests
./gradlew test

# E2E tests (requires AWS credentials)
export AWS_ACCESS_KEY_ID=your-key
export AWS_SECRET_ACCESS_KEY=your-secret
./gradlew e2eTest
```

## License

This project is licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## Support

- **Documentation**: [SlateDB Documentation](https://slatedb.io/docs)
- **Issues**: [GitHub Issues](https://github.com/slatedb/slatedb-java/issues)
- **Discussions**: [GitHub Discussions](https://github.com/slatedb/slatedb-java/discussions)

## Acknowledgments

Built on top of [SlateDB](https://github.com/slatedb/slatedb) - a cloud-native embedded storage engine.