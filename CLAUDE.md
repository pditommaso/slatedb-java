# SlateDB Java Client

A Java client for SlateDB using Foreign Function Interface (FFI) for high-performance native integration.

## Project Overview

This is a Java client implementation for SlateDB, a high-performance key-value database built in Rust. The client uses Java's Foreign Function Interface (FFI) available in Java 22+ to integrate directly with SlateDB's native Rust core, providing excellent performance while maintaining type safety and memory management.

### Key Features

- **Modern FFI Integration**: Uses Java Foreign Function Interface instead of JNI for cleaner, more performant native integration
- **Zero-Copy Operations**: Efficient memory sharing between Java and native code where possible
- **Arena-Based Memory Management**: Automatic cleanup of native resources using Java's Arena pattern
- **Pure Java Production Code**: Stable, maintainable code using proven Java patterns
- **Groovy + Spock Testing**: Expressive test framework for comprehensive validation

### Architecture

The client follows the proven patterns from SlateDB's Go and Python implementations:
- Core `SlateDB` class for read/write operations
- `DbReader` for read-only access with checkpoint support
- `WriteBatch` for atomic batch operations
- `Iterator` for efficient range queries
- Configuration classes with builder patterns

## Build System Requirements

### Gradle Groovy DSL
- Build system using Gradle with Groovy DSL syntax
- Java 24 toolchain requirement for FFI support
- Cross-platform native library compilation integration
- Rust/Cargo build integration for native libraries

### Dependencies
- **Production**: Minimal dependencies (SLF4J for logging)
- **Testing**: Groovy + Spock framework, JUnit platform
- **Native**: Rust toolchain with `cbindgen` for header generation

### Java Requirements
- **Java Version**: Java 24+ required for stable FFI support
- **Preview Features**: `--enable-preview` and `--add-modules jdk.incubator.foreign`
- **Platform Support**: Windows, macOS, Linux (x86_64 and ARM64)

## API Design Specifications

### Core SlateDB Class
```java
public class SlateDB implements AutoCloseable {
    // Database lifecycle
    public static SlateDB open(String path, StoreConfig storeConfig, SlateDBOptions options);
    public void close();
    public void flush();
    
    // Basic operations
    public void put(byte[] key, byte[] value);
    public byte[] get(byte[] key);
    public void delete(byte[] key);
    
    // Operations with options
    public void putWithOptions(byte[] key, byte[] value, PutOptions putOpts, WriteOptions writeOpts);
    public byte[] getWithOptions(byte[] key, ReadOptions readOpts);
    public void deleteWithOptions(byte[] key, WriteOptions writeOpts);
    
    // Batch operations
    public void write(WriteBatch batch);
    public void writeWithOptions(WriteBatch batch, WriteOptions opts);
    
    // Range queries
    public Iterator scan(byte[] start, byte[] end);
    public Iterator scanWithOptions(byte[] start, byte[] end, ScanOptions opts);
}
```

### DbReader for Read-Only Access
```java
public class DbReader implements AutoCloseable {
    public static DbReader open(String path, StoreConfig storeConfig, String checkpointId, DbReaderOptions opts);
    public byte[] get(byte[] key);
    public byte[] getWithOptions(byte[] key, ReadOptions opts);
    public Iterator scan(byte[] start, byte[] end);
    public Iterator scanWithOptions(byte[] start, byte[] end, ScanOptions opts);
    public void close();
}
```

### WriteBatch for Atomic Operations
```java
public class WriteBatch implements AutoCloseable {
    public WriteBatch();
    public void put(byte[] key, byte[] value);
    public void putWithOptions(byte[] key, byte[] value, PutOptions opts);
    public void delete(byte[] key);
    public void close();
}
```

### Iterator for Range Queries
```java
public class Iterator implements AutoCloseable {
    public boolean hasNext();
    public KeyValue next();
    public void close();
}

public class KeyValue {
    public byte[] getKey();
    public byte[] getValue();
}
```

## FFI Integration Details

### Native Library Integration
- **C API Layer**: Rust library compiled as `cdylib` with C-compatible exports
- **Header Generation**: Automatic C header generation using `cbindgen`
- **Symbol Loading**: Runtime symbol lookup using `Linker.nativeLinker()`
- **Method Handles**: Cached method handles for all native functions

### Memory Management Strategy
```java
// Arena-based memory management
try (Arena arena = Arena.ofConfined()) {
    MemorySegment keySegment = arena.allocateArray(ValueLayout.JAVA_BYTE, key);
    MemorySegment resultSegment = (MemorySegment) putMethodHandle.invoke(handle, keySegment, keySize);
    // Automatic cleanup when arena closes
}
```

### Error Handling from Native Code
- Native error codes mapped to Java exception hierarchy
- Detailed error messages preserved from Rust core
- Safe error propagation with guaranteed memory cleanup
- Consistent error handling across all operations

## Configuration Management

### StoreConfig - Object Storage Provider
```java
public class StoreConfig {
    public static Builder builder();
    
    public static class Builder {
        public Builder provider(Provider provider);
        public Builder aws(AWSConfig aws);
        public StoreConfig build();
    }
}

public enum Provider {
    LOCAL, AWS
}
```

### AWSConfig - AWS S3 Configuration
```java
public class AWSConfig {
    public static Builder builder();
    
    public static class Builder {
        public Builder bucket(String bucket);
        public Builder region(String region);
        public Builder endpoint(String endpoint);
        public Builder accessKey(String accessKey);
        public Builder secretKey(String secretKey);
        public Builder requestTimeout(Duration timeout);
        public AWSConfig build();
    }
}
```

### SlateDBOptions - Database Tuning
```java
public class SlateDBOptions {
    public static Builder builder();
    
    public static class Builder {
        public Builder l0SstSizeBytes(long bytes);
        public Builder flushInterval(Duration interval);
        public Builder cacheFolder(String folder);
        public Builder sstBlockSize(SstBlockSize size);
        public Builder compactorOptions(CompactorOptions options);
        public SlateDBOptions build();
    }
}
```

### Operation Options
```java
public class WriteOptions {
    public static Builder builder();
    public boolean isAwaitDurable();
}

public class ReadOptions {
    public static Builder builder();
    public DurabilityLevel getDurabilityFilter();
    public boolean isDirty();
}

public class PutOptions {
    public static Builder builder();
    public TTLType getTtlType();
    public long getTtlValue();
}

public class ScanOptions {
    public static Builder builder();
    public DurabilityLevel getDurabilityFilter();
    public boolean isDirty();
    public long getReadAheadBytes();
    public boolean isCacheBlocks();
    public long getMaxFetchTasks();
}
```

## Exception Hierarchy and Error Handling

### Exception Design
```java
// Base exception
public class SlateDBException extends Exception {
    public SlateDBException(String message);
    public SlateDBException(String message, Throwable cause);
}

// Specific exception types
public class SlateDBIOException extends SlateDBException {
    // I/O and network related errors
}

public class SlateDBInvalidArgumentException extends SlateDBException {
    // Invalid argument errors
}

public class SlateDBNotFoundException extends SlateDBException {
    // Key not found errors
}

public class SlateDBInternalException extends SlateDBException {
    // Internal database errors
}
```

### Error Code Mapping
- Map native error codes to appropriate Java exception types
- Preserve detailed error messages from Rust core
- Provide context-aware error information
- Ensure consistent error handling across all operations

## Testing Framework

### Unit Testing with Groovy + Spock
```groovy
class SlateDBSpec extends Specification {
    def "should open database with local storage"() {
        given:
        def storeConfig = StoreConfig.builder()
            .provider(Provider.LOCAL)
            .build()
        
        when:
        def db = SlateDB.open("/tmp/test-db", storeConfig, null)
        
        then:
        db != null
        
        cleanup:
        db?.close()
    }
    
    def "should handle batch operations atomically"() {
        given:
        def db = createTestDB()
        def batch = new WriteBatch()
        
        when:
        batch.put("key1".bytes, "value1".bytes)
        batch.put("key2".bytes, "value2".bytes)
        db.write(batch)
        
        then:
        db.get("key1".bytes) == "value1".bytes
        db.get("key2".bytes) == "value2".bytes
        
        cleanup:
        batch?.close()
        db?.close()
    }
}
```

### E2E Testing Configuration

#### Test Configuration
- **Default S3 Bucket**: `slatedb-sdk-dev`
- **Configurable Region**: Default `us-east-1`, configurable via properties/environment
- **AWS Credentials**: Support for environment variables and properties files
- **Test Isolation**: Unique prefixes to prevent interference between test runs

#### Configuration Sources (Priority Order)
1. System properties: `-Dslatedb.test.s3.bucket=my-bucket`
2. Environment variables: `SLATEDB_TEST_S3_BUCKET=my-bucket`
3. Properties file: `src/test/resources/test.properties`
4. Default values: `slatedb-sdk-dev` bucket, `us-east-1` region

#### Test Configuration Class
```java
public class TestConfig {
    public static AWSConfig getTestAWSConfig() {
        String bucket = getProperty("slatedb.test.s3.bucket", "slatedb-sdk-dev");
        String region = getProperty("slatedb.test.aws.region", "us-east-1");
        String accessKey = getProperty("slatedb.test.aws.accessKey", null);
        String secretKey = getProperty("slatedb.test.aws.secretKey", null);
        
        return AWSConfig.builder()
            .bucket(bucket)
            .region(region)
            .accessKey(accessKey)
            .secretKey(secretKey)
            .build();
    }
}
```

### E2E Test Implementation
```java
@TestMethodOrder(OrderAnnotation.class)
public class E2ETest {
    private static AWSConfig awsConfig;
    private static String testPrefix;
    
    @BeforeAll
    static void setupE2ETests() {
        awsConfig = TestConfig.getTestAWSConfig();
        testPrefix = "java-client-test-" + UUID.randomUUID().toString().substring(0, 8) + "/";
        
        assumeTrue(awsConfig.getAccessKey() != null, 
                  "AWS credentials not configured - skipping E2E tests");
    }
    
    @Test
    void testCRUDOperationsWithS3() {
        StoreConfig storeConfig = StoreConfig.builder()
            .provider(Provider.AWS)
            .aws(awsConfig)
            .build();
            
        try (SlateDB db = SlateDB.open("/tmp/slatedb-e2e-test", storeConfig, null)) {
            // Test CRUD operations
            db.put("test-key".getBytes(), "test-value".getBytes());
            assertArrayEquals("test-value".getBytes(), db.get("test-key".getBytes()));
            db.delete("test-key".getBytes());
            assertNull(db.get("test-key".getBytes()));
        }
    }
}
```

## Build Integration

### Gradle Configuration
```groovy
plugins {
    id 'java-library'
    id 'groovy'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

dependencies {
    implementation 'org.slf4j:slf4j-api:2.0.9'
    
    testImplementation 'org.spockframework:spock-core:2.3-groovy-4.0'
    testImplementation 'org.spockframework:spock-junit4:2.3-groovy-4.0'
    testImplementation 'junit:junit:4.13.2'
    testImplementation 'ch.qos.logback:logback-classic:1.4.11'
}

// Native library build tasks
task buildNative(type: Exec) {
    workingDir 'native'
    commandLine 'cargo', 'build', '--release'
}

task copyNativeLib(type: Copy, dependsOn: buildNative) {
    from 'native/target/release'
    include '**/*.so', '**/*.dylib', '**/*.dll'
    into 'src/main/resources/native'
}

compileJava {
    dependsOn copyNativeLib
    options.compilerArgs.addAll(['--enable-preview', '--add-modules', 'jdk.incubator.foreign'])
}

test {
    useJUnitPlatform()
    jvmArgs '--enable-preview', '--add-modules', 'jdk.incubator.foreign'
    
    systemProperty 'slatedb.test.s3.bucket', 
                   findProperty('slatedb.test.s3.bucket') ?: 'slatedb-sdk-dev'
    systemProperty 'slatedb.test.aws.region', 
                   findProperty('slatedb.test.aws.region') ?: 'us-east-1'
}
```

### Rust Native Library (Cargo.toml)
```toml
[package]
name = "slatedb-java"
version = "0.1.0"
edition = "2021"

[lib]
name = "slatedb_java"
crate-type = ["cdylib"]

[dependencies]
slatedb = { path = "../slatedb" }
serde = { version = "1.0", features = ["derive"] }
serde_json = "1.0"
tokio = { version = "1.0", features = ["rt", "rt-multi-thread"] }

[build-dependencies]
cbindgen = "0.27"
```

## Development Guidelines

### Production Code Standards
- **Pure Java**: Use stable Java features only, avoid experimental language constructs
- **Traditional Design**: Standard class hierarchies, builder patterns, interfaces
- **Minimal Dependencies**: Keep production classpath lean and focused
- **Thread Safety**: Proper synchronization where concurrent access is expected
- **Resource Management**: AutoCloseable pattern with try-with-resources support
- **Error Handling**: Comprehensive exception handling with meaningful messages

### Memory Management Patterns
- **Arena Usage**: Use confined arenas for automatic native memory cleanup
- **Scope Management**: Tie native memory lifetime to Java object scope
- **Zero-Copy Optimization**: Direct memory sharing where safe and beneficial
- **Resource Tracking**: Prevent use-after-free through handle validation

### API Design Principles
- **Consistency**: Match patterns from Go and Python clients where applicable
- **Builder Pattern**: For complex configuration objects
- **Method Overloading**: Provide both simple and advanced variants
- **Fluent Interfaces**: Where appropriate for configuration
- **Immutable Configurations**: Thread-safe, cacheable configuration objects

## Performance Considerations

### FFI Advantages
- **Reduced Overhead**: Direct native calls without JNI marshalling costs
- **Memory Efficiency**: Zero-copy operations and efficient memory sharing
- **Call Optimization**: Method handle caching and optimized call sites
- **GC Integration**: Better integration with Java garbage collector

### Optimization Strategies
- **Symbol Caching**: Cache native method handles for frequently called functions
- **Memory Pooling**: Reuse memory segments where safe and beneficial
- **Async Operations**: Consider virtual threads for non-blocking operations
- **Bulk Operations**: Optimize batch operations for better throughput

## CI/CD Integration

### Build Requirements
- Java 24+ toolchain
- Rust toolchain for native library compilation
- Cross-platform build matrix (Windows, macOS, Linux)
- Native library packaging and distribution

### Test Execution
- Unit tests run on all platforms
- E2E tests require AWS credentials configuration
- Test isolation with cleanup automation
- Performance benchmarking and regression detection

### Release Process
- Semantic versioning aligned with SlateDB core releases
- Multi-platform native library distribution
- Comprehensive documentation and examples
- Compatibility testing across Java versions

This comprehensive specification provides the complete foundation for implementing a high-performance, maintainable Java client for SlateDB using modern Java FFI capabilities.
