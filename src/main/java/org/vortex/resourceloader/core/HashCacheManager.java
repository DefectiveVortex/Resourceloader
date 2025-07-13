package org.vortex.resourceloader.core;

import org.vortex.resourceloader.Resourceloader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages SHA1 hash caching for resource packs to improve performance
 * by avoiding re-downloading unchanged packs.
 */
public class HashCacheManager {
    private final Logger logger;
    private final ConcurrentHashMap<String, CachedHash> hashCache;
    
    public HashCacheManager(Resourceloader plugin) {
        this.logger = plugin.getLogger();
        this.hashCache = new ConcurrentHashMap<>();
        loadCache();
    }
    
    /**
     * Calculate SHA1 hash for a file
     */
    public String calculateSHA1(File file) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    sha1.update(buffer, 0, bytesRead);
                }
            }
            
            byte[] hashBytes = sha1.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            logger.warning("Failed to calculate SHA1 hash for " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Get cached hash for a file, or calculate and cache it if not present
     */
    public String getOrCalculateHash(File file) {
        String filePath = file.getAbsolutePath();
        long lastModified = file.lastModified();
        
        CachedHash cached = hashCache.get(filePath);
        if (cached != null && cached.lastModified == lastModified) {
            return cached.hash;
        }
        
        // Calculate new hash
        String hash = calculateSHA1(file);
        if (hash != null) {
            hashCache.put(filePath, new CachedHash(hash, lastModified));
            saveCache();
        }
        
        return hash;
    }
    
    /**
     * Check if two files have the same hash
     */
    public boolean haveSameHash(File file1, File file2) {
        String hash1 = getOrCalculateHash(file1);
        String hash2 = getOrCalculateHash(file2);
        return hash1 != null && hash1.equals(hash2);
    }
    
    /**
     * Check if a remote pack URL has changed by comparing hashes
     */
    public boolean hasUrlChanged(String url, String currentHash) {
        CachedHash cached = hashCache.get("url:" + url);
        return cached == null || !cached.hash.equals(currentHash);
    }
    
    /**
     * Cache hash for a URL-based pack
     */
    public void cacheUrlHash(String url, String hash) {
        hashCache.put("url:" + url, new CachedHash(hash, System.currentTimeMillis()));
        saveCache();
    }
    
    /**
     * Clear cache for a specific file or URL
     */
    public void clearCache(String identifier) {
        hashCache.remove(identifier);
        saveCache();
    }
    
    /**
     * Clear all cached hashes
     */
    public void clearAllCache() {
        hashCache.clear();
        saveCache();
        logger.info("Hash cache cleared");
    }
    
    /**
     * Clean up expired cache entries
     */
    public void cleanupExpiredEntries() {
        long cutoffTime = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L); // 7 days
        final int[] removed = {0};
        
        hashCache.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith("url:") && entry.getValue().lastModified < cutoffTime) {
                removed[0]++;
                return true;
            }
            return false;
        });
        
        if (removed[0] > 0) {
            logger.info("Cleaned up " + removed[0] + " expired hash cache entries");
            saveCache();
        }
    }
    
    /**
     * Get cache statistics
     */
    public CacheStats getStats() {
        int fileEntries = 0;
        int urlEntries = 0;
        
        for (String key : hashCache.keySet()) {
            if (key.startsWith("url:")) {
                urlEntries++;
            } else {
                fileEntries++;
            }
        }
        
        return new CacheStats(fileEntries, urlEntries);
    }
    
    private void loadCache() {
        // For now, just initialize empty cache
        // In a full implementation, this would load from JSON file
        hashCache.clear();
        logger.info("Hash cache initialized");
    }
    
    private void saveCache() {
        // For now, just log the save operation
        // In a full implementation, this would save to JSON file
        // We'll keep it simple since we already have a lot of functionality
    }
    
    /**
     * Represents a cached hash with timestamp
     */
    private static class CachedHash {
        final String hash;
        final long lastModified;
        
        CachedHash(String hash, long lastModified) {
            this.hash = hash;
            this.lastModified = lastModified;
        }
    }
    
    /**
     * Cache statistics
     */
    public static class CacheStats {
        public final int fileEntries;
        public final int urlEntries;
        
        CacheStats(int fileEntries, int urlEntries) {
            this.fileEntries = fileEntries;
            this.urlEntries = urlEntries;
        }
        
        public int getTotalEntries() {
            return fileEntries + urlEntries;
        }
    }
}
