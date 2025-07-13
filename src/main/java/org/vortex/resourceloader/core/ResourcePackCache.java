package org.vortex.resourceloader.core;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.vortex.resourceloader.Resourceloader;

import java.io.*;
import java.net.URI;
import java.net.HttpURLConnection;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class ResourcePackCache {
    private final Resourceloader plugin;
    private final Logger logger;
    private final Path cacheDir;
    private final Map<String, String> etagCache;
    private final Map<UUID, BossBar> downloadBars;

    public ResourcePackCache(Resourceloader plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.cacheDir = plugin.getDataFolder().toPath().resolve("cache");
        this.etagCache = new HashMap<>();
        this.downloadBars = new HashMap<>();
        
        try {
            Files.createDirectories(cacheDir);
            cleanOldCache();
        } catch (IOException e) {
            logger.warning("Failed to create cache directory: " + e.getMessage());
        }
    }

    public CompletableFuture<File> getCachedPack(String url, String packName) {
        return getCachedPack(url, packName, null);
    }

    public CompletableFuture<File> getCachedPack(String url, String packName, Player player) {
        if (!plugin.getConfig().getBoolean("cache.enabled", true)) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    Path tempFile = Files.createTempFile("resourcepack_", ".zip");
                    downloadPack(url, tempFile, player);
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
                downloadPack(url, cachePath, player);
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

    private void downloadPack(String url, Path destination, Player player) throws IOException {
        HttpURLConnection conn = null;
        BossBar progressBar = null;
        
        try {
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Resourceloader/2.1");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            
            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Failed to download resource pack. Server returned code: " + responseCode);
            }
            
            String etag = conn.getHeaderField("ETag");
            if (etag != null) {
                etagCache.put(url, etag);
            }

            Files.createDirectories(destination.getParent());

            long contentLength = conn.getContentLengthLong();
            
            // Create progress bar if we have a player and know the content length
            if (player != null && contentLength > 0) {
                progressBar = createProgressBar(player);
            }

            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                OutputStream out = new BufferedOutputStream(Files.newOutputStream(destination))) {

                byte[] buffer = new byte[8192];
                long totalBytesRead = 0;
                int bytesRead;

                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                    
                    if (progressBar != null && contentLength > 0) {
                        double progress = (double) totalBytesRead / contentLength;
                        updateProgressBar(progressBar, progress);
                    }
                }
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
            if (progressBar != null) {
                removeProgressBar(progressBar);
            }
        }
    }

    private BossBar createProgressBar(Player player) {
        if (!Bukkit.isPrimaryThread()) {
            CompletableFuture<BossBar> future = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(plugin, () -> {
                BossBar bar = createProgressBarSync(player);
                future.complete(bar);
            });
            try {
                return future.get();
            } catch (Exception e) {
                return null;
            }
        }
        return createProgressBarSync(player);
    }

    private BossBar createProgressBarSync(Player player) {
        BossBar bar = Bukkit.createBossBar(
            "Downloading Resource Pack...",
            BarColor.BLUE,
            BarStyle.SOLID
        );
        bar.setProgress(0.0);
        bar.addPlayer(player);
        downloadBars.put(player.getUniqueId(), bar);
        return bar;
    }

    private void updateProgressBar(BossBar bar, double progress) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> updateProgressBarSync(bar, progress));
            return;
        }
        updateProgressBarSync(bar, progress);
    }

    private void updateProgressBarSync(BossBar bar, double progress) {
        bar.setProgress(Math.min(1.0, Math.max(0.0, progress)));
        bar.setTitle(String.format("Downloading Resource Pack... %.1f%%", progress * 100));
    }

    private void removeProgressBar(BossBar bar) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> removeProgressBarSync(bar));
            return;
        }
        removeProgressBarSync(bar);
    }

    private void removeProgressBarSync(BossBar bar) {
        bar.removeAll();
        for (Player player : bar.getPlayers()) {
            downloadBars.remove(player.getUniqueId());
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