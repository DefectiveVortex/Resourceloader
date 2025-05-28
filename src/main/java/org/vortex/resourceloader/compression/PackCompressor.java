package org.vortex.resourceloader.compression;

import org.bukkit.entity.Player;
import org.vortex.resourceloader.Resourceloader;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.zip.*;

public class PackCompressor {
    private final Resourceloader plugin;
    private final Logger logger;
    private final Map<String, Map<CompressionLevel, File>> compressionCache;
    private final Path cacheDir;
    private final ExecutorService compressionExecutor;
    private static final int BUFFER_SIZE = 32768; // 32KB buffer
    private static final long MAX_PACK_SIZE = 100 * 1024 * 1024; // 100MB
    private static final int MAX_THREADS = 4; // Limit concurrent compression tasks
    private final Map<String, CompletableFuture<File>> activeCompressions;

    public enum CompressionLevel {
        LOW(1),
        MEDIUM(6),
        HIGH(9);

        private final int level;

        CompressionLevel(int level) {
            this.level = level;
        }

        public int getLevel() {
            return level;
        }

        public static CompressionLevel fromString(String level) {
            try {
                return valueOf(level.toUpperCase());
            } catch (IllegalArgumentException e) {
                return MEDIUM;
            }
        }
    }

    public PackCompressor(Resourceloader plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.compressionCache = new ConcurrentHashMap<>();
        this.cacheDir = plugin.getDataFolder().toPath().resolve("compression_cache");
        this.compressionExecutor = new ThreadPoolExecutor(
            2, MAX_THREADS,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        this.activeCompressions = new ConcurrentHashMap<>();

        initialize();
    }

    private void initialize() {
        try {
            Files.createDirectories(cacheDir);
            cleanupOldCache();
        } catch (IOException e) {
            logger.warning("Failed to initialize compression cache: " + e.getMessage());
        }
    }

    public CompletableFuture<File> getCompressedPack(File originalPack, CompressionLevel level) {
        if (!plugin.getConfig().getBoolean("compression.enabled", true)) {
            return CompletableFuture.completedFuture(originalPack);
        }

        // Validate file size
        if (originalPack.length() > MAX_PACK_SIZE) {
            logger.warning("Pack " + originalPack.getName() + " exceeds maximum size limit of " + (MAX_PACK_SIZE / 1024 / 1024) + "MB");
            return CompletableFuture.completedFuture(originalPack);
        }

        String packName = originalPack.getName();
        
        // Check if compression is already in progress
        CompletableFuture<File> activeCompression = activeCompressions.get(packName);
        if (activeCompression != null && !activeCompression.isDone()) {
            return activeCompression;
        }

        Map<CompressionLevel, File> packCache = compressionCache.computeIfAbsent(
            packName, key -> new ConcurrentHashMap<>()
        );

        File cachedFile = packCache.get(level);
        if (cachedFile != null && cachedFile.exists() && 
            cachedFile.lastModified() >= originalPack.lastModified()) {
            return CompletableFuture.completedFuture(cachedFile);
        }

        CompletableFuture<File> future = CompletableFuture.supplyAsync(() -> {
            try {
                File compressedFile = compressPack(originalPack, level);
                packCache.put(level, compressedFile);
                return compressedFile;
            } catch (IOException e) {
                logger.warning("Failed to compress pack " + packName + ": " + e.getMessage());
                return originalPack;
            } finally {
                activeCompressions.remove(packName);
            }
        }, compressionExecutor);

        activeCompressions.put(packName, future);
        return future;
    }

    private File compressPack(File originalPack, CompressionLevel level) throws IOException {
        String baseName = originalPack.getName().replaceFirst("[.][^.]+$", "");
        File compressedFile = cacheDir.resolve(
            baseName + "_" + level.name().toLowerCase() + ".zip"
        ).toFile();

        // Create temp file for safe compression
        File tempFile = new File(compressedFile.getParent(), compressedFile.getName() + ".tmp");

        try (ZipFile sourceZip = new ZipFile(originalPack);
             ZipOutputStream zos = new ZipOutputStream(
                 new BufferedOutputStream(new FileOutputStream(tempFile)))) {
            
            zos.setLevel(level.getLevel());
            byte[] buffer = new byte[BUFFER_SIZE];

            // Get total entries for progress tracking
            int totalEntries = Collections.list(sourceZip.entries()).size();
            int processedEntries = 0;

            for (Enumeration<? extends ZipEntry> entries = sourceZip.entries(); 
                 entries.hasMoreElements();) {
                ZipEntry entry = entries.nextElement();
                ZipEntry newEntry = new ZipEntry(entry.getName());
                
                // Preserve original entry metadata
                newEntry.setTime(entry.getTime());
                newEntry.setComment(entry.getComment());
                if (entry.getExtra() != null) {
                    newEntry.setExtra(entry.getExtra());
                }

                zos.putNextEntry(newEntry);

                if (!entry.isDirectory()) {
                    try (InputStream in = new BufferedInputStream(
                            sourceZip.getInputStream(entry), BUFFER_SIZE)) {
                        int len;
                        while ((len = in.read(buffer)) > 0) {
                            zos.write(buffer, 0, len);
                        }
                    }
                }
                zos.closeEntry();
                
                processedEntries++;
                if (processedEntries % 50 == 0 || processedEntries == totalEntries) {
                    logger.info(String.format("Compressing %s: %d/%d entries (%.1f%%)", 
                        originalPack.getName(), processedEntries, totalEntries,
                        (processedEntries * 100.0) / totalEntries));
                }
            }
        }

        // Atomic rename of temp file to final file
        try {
            Files.move(tempFile.toPath(), compressedFile.toPath(), 
                      StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // Fallback to non-atomic move if atomic is not supported
            Files.move(tempFile.toPath(), compressedFile.toPath(), 
                      StandardCopyOption.REPLACE_EXISTING);
        }

        return compressedFile;
    }

    public CompressionLevel getOptimalCompressionLevel(Player player) {
        if (!plugin.getConfig().getBoolean("compression.auto-select", true)) {
            String defaultLevel = plugin.getConfig().getString(
                "compression.default-level", "medium"
            );
            return CompressionLevel.fromString(defaultLevel);
        }

        // Determine compression level based on player's ping and configured thresholds
        int ping = player.getPing();
        int excellentThreshold = plugin.getConfig().getInt("compression.thresholds.excellent", 50);
        int goodThreshold = plugin.getConfig().getInt("compression.thresholds.good", 150);
        
        if (ping < excellentThreshold) {
            return CompressionLevel.LOW;
        } else if (ping < goodThreshold) {
            return CompressionLevel.MEDIUM;
        } else {
            return CompressionLevel.HIGH;
        }
    }

    private void cleanupOldCache() {
        try {
            int cacheDuration = plugin.getConfig().getInt("cache.expiry-days", 7);
            long expiryTime = System.currentTimeMillis() - 
                            (cacheDuration * 24 * 60 * 60 * 1000L);

            Files.walk(cacheDir)
                .filter(path -> Files.isRegularFile(path))
                .forEach(path -> {
                    try {
                        if (Files.getLastModifiedTime(path).toMillis() < expiryTime) {
                            Files.delete(path);
                            logger.fine("Deleted expired cache file: " + path.getFileName());
                        }
                    } catch (IOException e) {
                        logger.warning("Failed to check/delete cached file: " + e.getMessage());
                    }
                });
        } catch (IOException e) {
            logger.warning("Failed to clean compression cache: " + e.getMessage());
        }
    }

    public void shutdown() {
        // Cancel any active compressions
        activeCompressions.values().forEach(future -> future.cancel(true));
        activeCompressions.clear();

        // Shutdown executor
        compressionExecutor.shutdown();
        try {
            if (!compressionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                compressionExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            compressionExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
} 