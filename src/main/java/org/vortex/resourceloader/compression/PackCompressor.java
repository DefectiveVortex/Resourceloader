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
        this.compressionExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors()
        );

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
        if (!plugin.getConfig().getBoolean("performance.compression.enabled", true)) {
            return CompletableFuture.completedFuture(originalPack);
        }

        String packName = originalPack.getName();
        Map<CompressionLevel, File> packCache = compressionCache.computeIfAbsent(
            packName, _ -> new ConcurrentHashMap<>()
        );

        File cachedFile = packCache.get(level);
        if (cachedFile != null && cachedFile.exists() && 
            cachedFile.lastModified() >= originalPack.lastModified()) {
            return CompletableFuture.completedFuture(cachedFile);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                File compressedFile = compressPack(originalPack, level);
                packCache.put(level, compressedFile);
                return compressedFile;
            } catch (IOException e) {
                logger.warning("Failed to compress pack " + packName + ": " + e.getMessage());
                return originalPack;
            }
        }, compressionExecutor);
    }

    private File compressPack(File originalPack, CompressionLevel level) throws IOException {
        String baseName = originalPack.getName().replaceFirst("[.][^.]+$", "");
        File compressedFile = cacheDir.resolve(
            baseName + "_" + level.name().toLowerCase() + ".zip"
        ).toFile();

        try (ZipFile sourceZip = new ZipFile(originalPack);
             ZipOutputStream zos = new ZipOutputStream(
                 new BufferedOutputStream(new FileOutputStream(compressedFile)))) {
            
            zos.setLevel(level.getLevel());
            byte[] buffer = new byte[BUFFER_SIZE];

            for (Enumeration<? extends ZipEntry> entries = sourceZip.entries(); 
                 entries.hasMoreElements();) {
                ZipEntry entry = entries.nextElement();
                ZipEntry newEntry = new ZipEntry(entry.getName());
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
            }
        }

        return compressedFile;
    }

    public CompressionLevel getOptimalCompressionLevel(Player player) {
        if (!plugin.getConfig().getBoolean("performance.compression.auto_select", true)) {
            String defaultLevel = plugin.getConfig().getString(
                "performance.compression.default_level", "medium"
            );
            return CompressionLevel.fromString(defaultLevel);
        }

        // Determine compression level based on player's ping
        int ping = player.getPing();
        
        if (ping < 50) { // Excellent connection
            return CompressionLevel.LOW;
        } else if (ping < 150) { // Good connection
            return CompressionLevel.MEDIUM;
        } else { // Poor connection
            return CompressionLevel.HIGH;
        }
    }

    private void cleanupOldCache() {
        try {
            int cacheDuration = plugin.getConfig().getInt("performance.cache_duration", 7);
            long expiryTime = System.currentTimeMillis() - 
                            (cacheDuration * 24 * 60 * 60 * 1000L);

            Files.walk(cacheDir)
                .filter(path -> Files.isRegularFile(path))
                .forEach(path -> {
                    try {
                        if (Files.getLastModifiedTime(path).toMillis() < expiryTime) {
                            Files.delete(path);
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
        compressionExecutor.shutdown();
        try {
            if (!compressionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                compressionExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            compressionExecutor.shutdownNow();
        }
    }
} 