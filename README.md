# SlateDB Java Client

A high-performance Java client for [SlateDB](https://github.com/slatedb/slatedb) using modern Java Foreign Function Interface (FFI) for native integration.

## Features

- **Modern FFI Integration**: Uses Java 24's Foreign Function Interface instead of JNI for better performance
- **Zero-Copy Operations**: Direct memory access between Java and native code
- **AWS S3 Backend**: Built-in support for S3 as object storage backend  
- **Comprehensive API**: Full CRUD operations, batch writes, and key iteration
- **Thread-Safe**: Concurrent operations supported
- **Memory Safe**: Automatic resource management with Arena-based memory allocation

## Requirements

- **Java 24+**: Required for Foreign Function Interface support
- **Gradle 8.14.3+**: Build system
- **Rust**: For building Go bindings native library
- **Go 1.21+**: For Go bindings compilation
- **AWS Credentials**: For S3 backend access (E2E tests)

## Quick Start

### 1. Build the Project

```bash
# Clone with upstream SlateDB
git clone https://github.com/slatedb/slatedb.git ../slatedb-upstream

# Build native library and Java project
./gradlew build
```

### 2. Basic Usage

```java
import com.slatedb.*;

// Configure S3 backend
StoreConfig storeConfig = StoreConfig.builder()
    .s3BucketName("my-slatedb-bucket")
    .s3Region("us-east-1") 
    .s3KeyPrefix("app-data/")
    .build();

// Configure database options
SlateDBOptions options = SlateDBOptions.builder()
    .flushIntervalMs(5000)
    .manifestPollIntervalMs(1000)
    .build();

// Open database
try (SlateDB db = SlateDB.open("/tmp/slatedb-data", storeConfig, options)) {
    // Basic operations
    db.put("key1", "value1");
    String value = db.get("key1");
    db.delete("key1");
    
    // Batch operations
    try (WriteBatch batch = WriteBatch.create()) {
        batch.put("batch-key-1", "batch-value-1");
        batch.put("batch-key-2", "batch-value-2");
        batch.delete("old-key");
        db.apply(batch);
    }
    
    // Iterate over keys
    try (Iterator iterator = db.iterator()) {
        while (iterator.hasNext()) {
            KeyValue kv = iterator.next();
            System.out.println(kv.key() + " -> " + kv.value());
        }
    }
}
```

## Testing

### Unit Tests

Run unit tests (no AWS credentials required):

```bash
./gradlew unitTest
```

### End-to-End Tests

Run E2E tests with AWS S3 backend:

```bash
# Set AWS credentials
export AWS_ACCESS_KEY_ID="your-access-key"
export AWS_SECRET_ACCESS_KEY="your-secret-key"

# Run E2E tests
./gradlew e2eTest
```

Configure test parameters:

```bash
./gradlew e2eTest \
  -Pslatedb.test.s3.bucket=my-test-bucket \
  -Pslatedb.test.aws.region=us-west-2
```

## GitHub Actions

The project includes CI/CD workflows:

- **Unit Tests**: Run on multiple platforms (Linux, macOS, Windows)
- **E2E Tests**: Run with AWS S3 integration

Configure secrets in your GitHub repository:
- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`

## Architecture

The Java client uses a layered architecture:

1. **Java API Layer**: Public Java interfaces (`SlateDB`, `WriteBatch`, etc.)
2. **FFI Integration**: Native method bindings using Java 24 FFI
3. **Go Bindings**: Reuses existing SlateDB Go bindings for C FFI
4. **SlateDB Core**: Native Rust implementation

## Configuration

### StoreConfig

Configure object storage backend:

```java
StoreConfig config = StoreConfig.builder()
    .s3BucketName("bucket-name")           // Required
    .s3Region("us-east-1")                 // Required  
    .s3KeyPrefix("optional/prefix/")       // Optional
    .build();
```

### SlateDBOptions

Configure database behavior:

```java
SlateDBOptions options = SlateDBOptions.builder()
    .flushIntervalMs(5000)                 // Flush frequency
    .manifestPollIntervalMs(1000)          // Manifest polling
    .minFilterKeys(10000)                  // Bloom filter threshold
    .build();
```

## Performance

The Java client leverages modern FFI for optimal performance:

- **Zero-copy memory operations** between Java and native code
- **Arena-based memory management** for efficient resource cleanup  
- **Direct method handles** instead of JNI overhead
- **Reuses proven Go bindings** for stability and compatibility

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make changes with tests
4. Ensure CI passes
5. Submit a pull request

## License

Apache License 2.0 - see LICENSE file for details.

## Links

- [SlateDB Project](https://github.com/slatedb/slatedb)
- [Java FFI Documentation](https://openjdk.org/jeps/454)
- [API Documentation](./CLAUDE.md)