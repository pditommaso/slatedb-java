package com.slatedb;

import com.slatedb.config.*;
import com.slatedb.exceptions.SlateDBException;
import com.slatedb.exceptions.SlateDBInvalidArgumentException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test version of SlateDB class that uses MockNative for testing without requiring actual native library.
 */
public final class SlateDBTest implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(SlateDBTest.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final long handle;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    private SlateDBTest(long handle) {
        this.handle = handle;
    }
    
    /**
     * Opens a SlateDB database with the specified configuration.
     */
    public static SlateDBTest open(String path, StoreConfig storeConfig, SlateDBOptions options) throws SlateDBException {
        Objects.requireNonNull(path, "Path cannot be null");
        Objects.requireNonNull(storeConfig, "Store config cannot be null");
        
        try {
            // Convert configurations to JSON
            String storeConfigJson = serializeStoreConfig(storeConfig);
            String optionsJson = options != null ? serializeOptions(options) : null;
            
            // Open database using mock native
            long handle = MockNative.slatedb_open(path, storeConfigJson, optionsJson);
            
            if (handle <= 0) {
                throw new SlateDBException("Failed to open database");
            }
            
            return new SlateDBTest(handle);
            
        } catch (SlateDBException e) {
            throw e;
        } catch (Exception e) {
            throw new SlateDBException("Failed to open database: " + e.getMessage(), e);
        }
    }
    
    /**
     * Stores a key-value pair in the database.
     */
    public void put(byte[] key, byte[] value) throws SlateDBException {
        put(key, value, null, null);
    }
    
    /**
     * Stores a key-value pair in the database with options.
     */
    public void put(byte[] key, byte[] value, PutOptions putOptions, WriteOptions writeOptions) throws SlateDBException {
        validateKey(key);
        Objects.requireNonNull(value, "Value cannot be null");
        ensureNotClosed();
        
        try {
            String putOptionsJson = putOptions != null ? serializePutOptions(putOptions) : null;
            String writeOptionsJson = writeOptions != null ? serializeWriteOptions(writeOptions) : null;
            
            int result = MockNative.slatedb_put(handle, key, value, putOptionsJson, writeOptionsJson);
            if (result != 0) {
                throw new SlateDBException("Put operation failed");
            }
        } catch (SlateDBException e) {
            throw e;
        } catch (Exception e) {
            throw new SlateDBException("Put operation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Retrieves a value by key from the database.
     */
    public byte[] get(byte[] key) throws SlateDBException {
        return get(key, null);
    }
    
    /**
     * Retrieves a value by key from the database with options.
     */
    public byte[] get(byte[] key, ReadOptions readOptions) throws SlateDBException {
        validateKey(key);
        ensureNotClosed();
        
        try {
            String readOptionsJson = readOptions != null ? serializeReadOptions(readOptions) : null;
            return MockNative.slatedb_get(handle, key, readOptionsJson);
        } catch (Exception e) {
            throw new SlateDBException("Get operation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Deletes a key-value pair from the database.
     */
    public void delete(byte[] key) throws SlateDBException {
        delete(key, null);
    }
    
    /**
     * Deletes a key-value pair from the database with options.
     */
    public void delete(byte[] key, WriteOptions writeOptions) throws SlateDBException {
        validateKey(key);
        ensureNotClosed();
        
        try {
            String writeOptionsJson = writeOptions != null ? serializeWriteOptions(writeOptions) : null;
            int result = MockNative.slatedb_delete(handle, key, writeOptionsJson);
            if (result != 0) {
                throw new SlateDBException("Delete operation failed");
            }
        } catch (SlateDBException e) {
            throw e;
        } catch (Exception e) {
            throw new SlateDBException("Delete operation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Flushes any pending writes to durable storage.
     */
    public void flush() throws SlateDBException {
        ensureNotClosed();
        
        try {
            int result = MockNative.slatedb_flush(handle);
            if (result != 0) {
                throw new SlateDBException("Flush operation failed");
            }
        } catch (Exception e) {
            if (e instanceof SlateDBException) {
                throw e;
            }
            throw new SlateDBException("Flush operation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Applies a write batch atomically.
     */
    public void write(WriteBatchTest writeBatch) throws SlateDBException {
        write(writeBatch, null);
    }
    
    /**
     * Applies a write batch atomically with options.
     */
    public void write(WriteBatchTest writeBatch, WriteOptions writeOptions) throws SlateDBException {
        Objects.requireNonNull(writeBatch, "Write batch cannot be null");
        ensureNotClosed();
        
        try {
            String writeOptionsJson = writeOptions != null ? serializeWriteOptions(writeOptions) : null;
            int result = MockNative.slatedb_write(handle, writeBatch.getHandle(), writeOptionsJson);
            if (result != 0) {
                throw new SlateDBException("Write batch operation failed");
            }
            // Mark batch as written to prevent reuse
            writeBatch.markAsWritten();
        } catch (SlateDBException e) {
            throw e;
        } catch (Exception e) {
            throw new SlateDBException("Write batch operation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Creates an iterator for scanning a key range.
     */
    public Iterator scan(byte[] start, byte[] end) throws SlateDBException {
        return scan(start, end, null);
    }
    
    /**
     * Creates an iterator for scanning a key range with options.
     */
    public Iterator scan(byte[] start, byte[] end, ScanOptions scanOptions) throws SlateDBException {
        ensureNotClosed();
        
        try {
            String scanOptionsJson = scanOptions != null ? serializeScanOptions(scanOptions) : null;
            long iterHandle = MockNative.slatedb_scan(handle, start, end, scanOptionsJson);
            if (iterHandle <= 0) {
                throw new SlateDBException("Failed to create iterator");
            }
            // Create a mock MemorySegment for testing
            MemorySegment mockHandle = MemorySegment.ofAddress(iterHandle);
            return new Iterator(mockHandle);
        } catch (SlateDBException e) {
            throw e;
        } catch (Exception e) {
            throw new SlateDBException("Scan operation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Checks if the database is closed.
     */
    public boolean isClosed() {
        return closed.get() || MockNative.slatedb_is_closed(handle);
    }
    
    /**
     * Closes the database and releases all resources.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                MockNative.slatedb_close(handle);
            } catch (Exception e) {
                logger.warn("Error closing database", e);
            }
        }
    }
    
    // Helper methods
    
    private void ensureNotClosed() throws SlateDBException {
        if (isClosed()) {
            throw new SlateDBException("Database is closed");
        }
    }
    
    private void validateKey(byte[] key) throws SlateDBInvalidArgumentException {
        if (key == null || key.length == 0) {
            throw new SlateDBInvalidArgumentException("Key cannot be null or empty");
        }
    }
    
    private static String serializeStoreConfig(StoreConfig storeConfig) throws Exception {
        return objectMapper.writeValueAsString(storeConfig);
    }
    
    private static String serializeOptions(SlateDBOptions options) throws Exception {
        return objectMapper.writeValueAsString(options);
    }
    
    private static String serializePutOptions(PutOptions options) throws Exception {
        return objectMapper.writeValueAsString(options);
    }
    
    private static String serializeWriteOptions(WriteOptions options) throws Exception {
        return objectMapper.writeValueAsString(options);
    }
    
    private static String serializeReadOptions(ReadOptions options) throws Exception {
        return objectMapper.writeValueAsString(options);
    }
    
    private static String serializeScanOptions(ScanOptions options) throws Exception {
        return objectMapper.writeValueAsString(options);
    }
}