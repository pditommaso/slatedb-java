package com.slatedb;

import com.slatedb.config.PutOptions;
import com.slatedb.exceptions.SlateDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test version of WriteBatch class that uses MockNative for testing.
 */
public final class WriteBatchTest implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(WriteBatchTest.class);
    
    private final long handle;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean written = new AtomicBoolean(false);
    
    /**
     * Creates a new write batch.
     */
    public WriteBatchTest() {
        this.handle = MockNative.slatedb_write_batch_new();
    }
    
    /**
     * Adds a put operation to the batch.
     */
    public void put(byte[] key, byte[] value) throws SlateDBException {
        put(key, value, null);
    }
    
    /**
     * Adds a put operation to the batch with options.
     */
    public void put(byte[] key, byte[] value, PutOptions putOptions) throws SlateDBException {
        ensureNotClosed();
        ensureNotWritten();
        
        if (key == null || key.length == 0) {
            throw new SlateDBException("Key cannot be null or empty");
        }
        if (value == null) {
            throw new SlateDBException("Value cannot be null");
        }
        
        try {
            String putOptionsJson = putOptions != null ? serializePutOptions(putOptions) : null;
            int result = MockNative.slatedb_write_batch_put(handle, key, value, putOptionsJson);
            if (result != 0) {
                throw new SlateDBException("Failed to add put operation to batch");
            }
        } catch (SlateDBException e) {
            throw e;
        } catch (Exception e) {
            throw new SlateDBException("Failed to add put operation: " + e.getMessage(), e);
        }
    }
    
    /**
     * Adds a delete operation to the batch.
     */
    public void delete(byte[] key) throws SlateDBException {
        ensureNotClosed();
        ensureNotWritten();
        
        if (key == null || key.length == 0) {
            throw new SlateDBException("Key cannot be null or empty");
        }
        
        try {
            int result = MockNative.slatedb_write_batch_delete(handle, key);
            if (result != 0) {
                throw new SlateDBException("Failed to add delete operation to batch");
            }
        } catch (SlateDBException e) {
            throw e;
        } catch (Exception e) {
            throw new SlateDBException("Failed to add delete operation: " + e.getMessage(), e);
        }
    }
    
    /**
     * Gets the native handle for this batch.
     */
    public long getHandle() {
        return handle;
    }
    
    /**
     * Marks this batch as written (called by SlateDB after successful write).
     */
    public void markAsWritten() {
        written.set(true);
    }
    
    /**
     * Checks if this batch has been written.
     */
    public boolean isWritten() {
        return written.get();
    }
    
    /**
     * Checks if this batch is closed.
     */
    public boolean isClosed() {
        return closed.get();
    }
    
    /**
     * Closes the batch and releases resources.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                MockNative.slatedb_write_batch_close(handle);
            } catch (Exception e) {
                logger.warn("Error closing write batch", e);
            }
        }
    }
    
    private void ensureNotClosed() throws SlateDBException {
        if (closed.get()) {
            throw new SlateDBException("WriteBatch is closed");
        }
    }
    
    private void ensureNotWritten() throws SlateDBException {
        if (written.get()) {
            throw new SlateDBException("WriteBatch has already been written and cannot be reused");
        }
    }
    
    private String serializePutOptions(PutOptions options) throws Exception {
        // Simple mock serialization
        return "{}";
    }
}