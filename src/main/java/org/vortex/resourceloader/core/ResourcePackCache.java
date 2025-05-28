package org.vortex.resourceloader.core;

import java.io.*;
import java.net.URI;
import java.net.HttpURLConnection;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.vortex.resourceloader.Resourceloader;

public class ResourcePackCache {
    private final Resourceloader plugin;
    private final Logger logger;
    private final Path cacheDir;
    private final Map<String, String> etagCache;

    public ResourcePackCache(Resourceloader plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.cacheDir = plugin.getDataFolder().toPath().resolve("cache");
        this.etagCache = new HashMap<>();
        
        try {
            Files.createDirectories(cacheDir);
            cleanOldCache();
        } catch (IOException e) {
            logger.warning("Failed to create cache directory: " + e.getMessage());
        }
    }

    public CompletableFuture<File> getCachedPack(String url, String packName) {
        if (!plugin.getConfig().getBoolean("cache.enabled", true)) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    Path tempFile = Files.createTempFile("resourcepack_", ".zip");
                    downloadPack(url, tempFile);
                    return tempFile.toFile();
                } catch (IOException e) {
                    logger.warning("Failed to download resource pack " + packName + ": " + e.getMessage());
                    throw new RuntimeException(e);
                }
            });
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String cachedEtag = etagCache.get(url);
                Path cachePath = cacheDir.resolve(packName + "_" + getUrlHash(url) + ".zip");
                
                if (Files.exists(cachePath)) {
                    HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
                    conn.setRequestMethod("HEAD");
                    if (cachedEtag != null) {
                        conn.setRequestProperty("If-None-Match", cachedEtag);
                    }

                    int responseCode = conn.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                        logger.info("Using cached version of " + packName);
                        return cachePath.toFile();
                    }

                    String newEtag = conn.getHeaderField("ETag");
                    if (newEtag != null && newEtag.equals(cachedEtag)) {
                        return cachePath.toFile();
                    }
                }

                logger.info("Downloading and caching " + packName);
                downloadPack(url, cachePath);
                return cachePath.toFile();
            } catch (IOException e) {
                logger.warning("Failed to cache resource pack " + packName + ": " + e.getMessage());
                throw new RuntimeException("Failed to cache resource pack: " + e.getMessage(), e);
            } catch (Exception e) {
                logger.warning("Unexpected error while caching resource pack " + packName + ": " + e.getMessage());
                throw new RuntimeException("Unexpected error while caching resource pack: " + e.getMessage(), e);
            }
        });
    }

    private void downloadPack(String url, Path destination) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        String etag = conn.getHeaderField("ETag");
        if (etag != null) {
            etagCache.put(url, etag);
        }

        try (InputStream in = new BufferedInputStream(conn.getInputStream(), 32768);
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(destination), 32768)) {
            byte[] buffer = new byte[32768];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }

    private String getUrlHash(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 8);
        } catch (Exception e) {
            return String.valueOf(url.hashCode());
        }
    }

    private void cleanOldCache() {
        int expiryDays = plugin.getConfig().getInt("cache.expiry-days", 7);
        long expiryMillis = System.currentTimeMillis() - (expiryDays * 24 * 60 * 60 * 1000L);

        try {
            Files.walk(cacheDir)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        if (Files.getLastModifiedTime(file).toMillis() < expiryMillis) {
                            Files.delete(file);
                            logger.info("Deleted expired cache file: " + file.getFileName());
                        }
                    } catch (IOException e) {
                        logger.warning("Failed to check/delete cache file: " + e.getMessage());
                    }
                });
        } catch (IOException e) {
            logger.warning("Failed to clean cache directory: " + e.getMessage());
        }
    }

    public void clearCache() {
        try {
            Files.walk(cacheDir)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        Files.delete(file);
                    } catch (IOException e) {
                        logger.warning("Failed to delete cache file: " + file.getFileName());
                    }
                });
            etagCache.clear();
            logger.info("Resource pack cache cleared");
        } catch (IOException e) {
            logger.warning("Failed to clear cache directory: " + e.getMessage());
        }
    }
} 