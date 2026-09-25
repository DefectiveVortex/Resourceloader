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
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class ResourcePackCache {
    private final Resourceloader plugin;
    private final Logger logger;
    private final Path cacheDir;
    private static final String ETAG_SUFFIX = "|etag";
    private static final String LAST_MODIFIED_SUFFIX = "|last-modified";
    private static final long REVALIDATE_MILLIS = 60_000;
    private final Path validatorsFile;
    private final Properties validators;
    private final Map<String, Long> lastValidated;
    private final Map<UUID, BossBar> downloadBars;

    public ResourcePackCache(Resourceloader plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.cacheDir = plugin.getDataFolder().toPath().resolve("cache");
        this.validatorsFile = cacheDir.resolve("cache-meta.properties");
        this.validators = new Properties();
        this.lastValidated = new ConcurrentHashMap<>();
        this.downloadBars = new ConcurrentHashMap<>();

        try {
            Files.createDirectories(cacheDir);
            cleanOldCache();
            if (Files.isRegularFile(validatorsFile)) {
                try (InputStream in = Files.newInputStream(validatorsFile)) {
                    validators.load(in);
                }
            }
        } catch (IOException e) {
            logger.warning("Failed to create cache directory: " + e.getMessage());
        }
    }

    public CompletableFuture<File> getCachedPack(String url, String packName) {
        return getCachedPack(url, packName, null);
    }

    public CompletableFuture<File> getCachedPack(String url, String packName, Player player) {
        return CompletableFuture.supplyAsync(() -> {
            // The pack server only serves files from the packs and cache folders, so even with
            // caching disabled the download has to land in the cache folder
            Path cachePath = cacheDir.resolve(packName + "_" + getUrlHash(url) + ".zip");
            boolean useCache = plugin.getConfig().getBoolean("cache.enabled", true);
            boolean cached = useCache && Files.isRegularFile(cachePath);

            // Several players joining at once should not each hit the origin
            Long checked = lastValidated.get(url);
            if (cached && checked != null && System.currentTimeMillis() - checked < REVALIDATE_MILLIS) {
                return cachePath.toFile();
            }

            try {
                if (downloadPack(url, cachePath, player, cached)) {
                    logger.info("Downloaded and cached " + packName);
                } else {
                    logger.info("Using cached version of " + packName);
                }
                lastValidated.put(url, System.currentTimeMillis());
                return cachePath.toFile();
            } catch (IOException e) {
                if (cached) {
                    logger.warning("Could not reach " + url + " (" + e.getMessage() + "); using cached copy of "
                        + packName);
                    return cachePath.toFile();
                }
                logger.warning("Failed to cache resource pack " + packName + ": " + e.getMessage());
                throw new RuntimeException("Failed to cache resource pack: " + e.getMessage(), e);
            } catch (Exception e) {
                logger.warning("Unexpected error while caching resource pack " + packName + ": " + e.getMessage());
                throw new RuntimeException("Unexpected error while caching resource pack: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Downloads {@code url} into {@code destination}. With {@code conditional}, the request carries the
     * validators of the cached copy and nothing is downloaded if the origin says it is unchanged.
     *
     * @return true if a new copy was downloaded, false if the cached copy is still current
     */
    private boolean downloadPack(String url, Path destination, Player player, boolean conditional) throws IOException {
        HttpURLConnection conn = null;
        BossBar progressBar = null;
        Path tempFile = null;

        try {
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Resourceloader/" + plugin.getDescription().getVersion());
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            if (conditional) {
                String etag = validators.getProperty(url + ETAG_SUFFIX);
                String lastModified = validators.getProperty(url + LAST_MODIFIED_SUFFIX);
                if (etag != null) {
                    conn.setRequestProperty("If-None-Match", etag);
                }
                if (lastModified != null) {
                    conn.setRequestProperty("If-Modified-Since", lastModified);
                }
            }

            int responseCode = conn.getResponseCode();
            if (conditional && responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                return false;
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Failed to download resource pack. Server returned code: " + responseCode);
            }

            Files.createDirectories(destination.getParent());
            tempFile = Files.createTempFile(destination.getParent(), "download_", ".tmp");

            long contentLength = conn.getContentLengthLong();

            // Create progress bar if we have a player and know the content length
            if (player != null && contentLength > 0) {
                progressBar = createProgressBar(player);
            }

            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                    OutputStream out = new BufferedOutputStream(Files.newOutputStream(tempFile))) {

                byte[] buffer = new byte[8192];
                long totalBytesRead = 0;
                int bytesRead;

                long lastUpdateTime = 0;
                double lastProgress = 0;

                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;

                    if (progressBar != null && contentLength > 0) {
                        double progress = (double) totalBytesRead / contentLength;
                        long currentTime = System.currentTimeMillis();

                        // Throttle updates: Max 1 update per 500ms OR if progress changed by > 1%, and
                        // always at 100%
                        if (currentTime - lastUpdateTime > 500 || progress - lastProgress >= 0.01 || progress >= 1.0) {
                            updateProgressBar(progressBar, progress);
                            lastUpdateTime = currentTime;
                            lastProgress = progress;
                        }
                    }
                }
            }

            // Only a complete download replaces the cached copy
            Files.move(tempFile, destination, StandardCopyOption.REPLACE_EXISTING);
            tempFile = null;
            rememberValidators(url, conn.getHeaderField("ETag"), conn.getHeaderField("Last-Modified"));
            return true;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
            if (progressBar != null) {
                removeProgressBar(progressBar);
            }
            if (tempFile != null) {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    private synchronized void rememberValidators(String url, String etag, String lastModified) {
        setOrRemove(url + ETAG_SUFFIX, etag);
        setOrRemove(url + LAST_MODIFIED_SUFFIX, lastModified);
        try (OutputStream out = Files.newOutputStream(validatorsFile)) {
            validators.store(out, "Resourceloader: HTTP validators of cached URL packs");
        } catch (IOException e) {
            logger.warning("Failed to save cache metadata: " + e.getMessage());
        }
    }

    private void setOrRemove(String key, String value) {
        if (value == null) {
            validators.remove(key);
        } else {
            validators.setProperty(key, value);
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
                BarStyle.SOLID);
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
        for (Player player : bar.getPlayers()) {
            downloadBars.remove(player.getUniqueId());
        }
        bar.removeAll();
    }

    private String getUrlHash(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
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
                    .filter(file -> !file.equals(validatorsFile))
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
            synchronized (this) {
                validators.clear();
            }
            lastValidated.clear();
            logger.info("Resource pack cache cleared");
        } catch (IOException e) {
            logger.warning("Failed to clear cache directory: " + e.getMessage());
        }
    }
}
