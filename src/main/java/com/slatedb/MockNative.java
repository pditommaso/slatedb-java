package com.slatedb;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mock implementation of Native.java for testing without requiring actual native library.
 * This allows us to test the Java code structure and framework setup.
 */
public class MockNative {
    
    private static final AtomicLong handleCounter = new AtomicLong(1);
    private static final Map<Long, MockDatabase> databases = new ConcurrentHashMap<>();
    private static final Map<Long, MockIterator> iterators = new ConcurrentHashMap<>();
    private static final Map<Long, MockWriteBatch> batches = new ConcurrentHashMap<>();
    
    // Mock database implementation
    private static class MockDatabase {
        private final Map<String, byte[]> data = new ConcurrentHashMap<>();
        private final long handle;
        private boolean closed = false;
        
        public MockDatabase(long handle) {
            this.handle = handle;
        }
        
        public void put(byte[] key, byte[] value) {
            if (closed) throw new RuntimeException("Database is closed");
            data.put(new String(key), value.clone());
        }
        
        public byte[] get(byte[] key) {
            if (closed) throw new RuntimeException("Database is closed");
            byte[] value = data.get(new String(key));
            return value != null ? value.clone() : null;
        }
        
        public void delete(byte[] key) {
            if (closed) throw new RuntimeException("Database is closed");
            data.remove(new String(key));
        }
        
        public void close() {
            closed = true;
            data.clear();
        }
        
        public boolean isClosed() {
            return closed;
        }
    }
    
    // Mock iterator implementation
    private static class MockIterator {
        private final long handle;
        private boolean closed = false;
        
        public MockIterator(long handle) {
            this.handle = handle;
        }
        
        public void close() {
            closed = true;
        }
        
        public boolean isClosed() {
            return closed;
        }
    }
    
    // Mock write batch implementation
    private static class MockWriteBatch {
        private final Map<String, byte[]> puts = new ConcurrentHashMap<>();
        private final Set<String> deletes = ConcurrentHashMap.newKeySet();
        private final long handle;
        private boolean closed = false;
        
        public MockWriteBatch(long handle) {
            this.handle = handle;
        }
        
        public void put(byte[] key, byte[] value) {
            if (closed) throw new RuntimeException("WriteBatch is closed");
            puts.put(new String(key), value.clone());
        }
        
        public void delete(byte[] key) {
            if (closed) throw new RuntimeException("WriteBatch is closed");
            deletes.add(new String(key));
        }
        
        public void close() {
            closed = true;
            puts.clear();
            deletes.clear();
        }
        
        public boolean isClosed() {
            return closed;
        }
    }
    
    // Mock native functions
    
    public static long slatedb_open(String path, String storeConfigJson, String optionsJson) {
        long handle = handleCounter.getAndIncrement();
        databases.put(handle, new MockDatabase(handle));
        return handle;
    }
    
    public static void slatedb_close(long handle) {
        MockDatabase db = databases.remove(handle);
        if (db != null) {
            db.close();
        }
    }
    
    public static int slatedb_put(long handle, byte[] key, byte[] value, String putOptionsJson, String writeOptionsJson) {
        MockDatabase db = databases.get(handle);
        if (db == null) return -1;
        try {
            db.put(key, value);
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }
    
    public static byte[] slatedb_get(long handle, byte[] key, String readOptionsJson) {
        MockDatabase db = databases.get(handle);
        if (db == null) return null;
        return db.get(key);
    }
    
    public static int slatedb_delete(long handle, byte[] key, String writeOptionsJson) {
        MockDatabase db = databases.get(handle);
        if (db == null) return -1;
        try {
            db.delete(key);
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }
    
    public static int slatedb_flush(long handle) {
        MockDatabase db = databases.get(handle);
        return (db != null && !db.isClosed()) ? 0 : -1;
    }
    
    public static long slatedb_write_batch_new() {
        long handle = handleCounter.getAndIncrement();
        batches.put(handle, new MockWriteBatch(handle));
        return handle;
    }
    
    public static void slatedb_write_batch_close(long handle) {
        MockWriteBatch batch = batches.remove(handle);
        if (batch != null) {
            batch.close();
        }
    }
    
    public static int slatedb_write_batch_put(long handle, byte[] key, byte[] value, String putOptionsJson) {
        MockWriteBatch batch = batches.get(handle);
        if (batch == null) return -1;
        try {
            batch.put(key, value);
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }
    
    public static int slatedb_write_batch_delete(long handle, byte[] key) {
        MockWriteBatch batch = batches.get(handle);
        if (batch == null) return -1;
        try {
            batch.delete(key);
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }
    
    public static int slatedb_write(long dbHandle, long batchHandle, String writeOptionsJson) {
        MockDatabase db = databases.get(dbHandle);
        MockWriteBatch batch = batches.get(batchHandle);
        if (db == null || batch == null) return -1;
        
        try {
            // Apply all puts
            for (Map.Entry<String, byte[]> entry : batch.puts.entrySet()) {
                db.put(entry.getKey().getBytes(), entry.getValue());
            }
            
            // Apply all deletes
            for (String key : batch.deletes) {
                db.delete(key.getBytes());
            }
            
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }
    
    public static long slatedb_scan(long handle, byte[] start, byte[] end, String scanOptionsJson) {
        MockDatabase db = databases.get(handle);
        if (db == null) return -1;
        
        long iterHandle = handleCounter.getAndIncrement();
        iterators.put(iterHandle, new MockIterator(iterHandle));
        return iterHandle;
    }
    
    public static void slatedb_iterator_close(long handle) {
        MockIterator iterator = iterators.remove(handle);
        if (iterator != null) {
            iterator.close();
        }
    }
    
    public static boolean slatedb_is_closed(long handle) {
        MockDatabase db = databases.get(handle);
        return db == null || db.isClosed();
    }
    
    public static boolean slatedb_iterator_is_closed(long handle) {
        MockIterator iterator = iterators.get(handle);
        return iterator == null || iterator.isClosed();
    }
    
    // Mock error message function
    public static String slatedb_get_last_error() {
        return "Mock error message";
    }
}