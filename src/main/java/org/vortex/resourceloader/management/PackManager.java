package org.vortex.resourceloader.management;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.vortex.resourceloader.Resourceloader;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.zip.*;

public class PackManager {
    private final Resourceloader plugin;
    private final Logger logger;
    private final Path backupDir;
    private final ScheduledExecutorService scheduler;
    private final Map<String, PackStatistics> packStats;
    private final Map<UUID, List<String>> playerPreferences;
    private final Path statsFile;
    private final Path preferencesFile;
    private static final int DEFAULT_BACKUP_INTERVAL = 24;
    private static final int DEFAULT_MAX_PREFERENCES = 5;
    private static final int SHUTDOWN_TIMEOUT = 5;
    private volatile boolean isShutdown = false;

    public PackManager(Resourceloader plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.backupDir = plugin.getDataFolder().toPath().resolve("backups");
        this.statsFile = plugin.getDataFolder().toPath().resolve("statistics.yml");
        this.preferencesFile = plugin.getDataFolder().toPath().resolve("preferences.yml");
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "PackManager-Scheduler");
            t.setDaemon(true);
            return t;
        });
        this.packStats = new ConcurrentHashMap<>();
        this.playerPreferences = new ConcurrentHashMap<>();

        initialize();
    }

    private void initialize() {
        try {
            Files.createDirectories(backupDir);
            loadStatistics();
            loadPreferences();
            setupBackupSchedule();
        } catch (IOException e) {
            logger.severe("Failed to initialize pack manager: " + e.getMessage());
            logger.severe("Some functionality may be limited. Please check file permissions and disk space.");
        }
    }

    public void createBackup() {
        if (isShutdown) {
            logger.warning("Attempted to create backup after shutdown");
            return;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path backupFile = backupDir.resolve("backup_" + timestamp + ".zip");
        Path tempFile = backupFile.resolveSibling(backupFile.getFileName().toString() + ".tmp");
        
        try {
            Path packsDir = plugin.getDataFolder().toPath().resolve("packs");
            if (!Files.exists(packsDir)) {
                logger.warning("Packs directory does not exist, skipping backup");
                return;
            }

            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(tempFile), 8192))) {
                Files.walk(packsDir)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        try {
                            String zipPath = packsDir.relativize(path).toString();
                            zos.putNextEntry(new ZipEntry(zipPath));
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            logger.warning("Failed to backup file " + path + ": " + e.getMessage());
                        }
                    });
            }

            // Atomic move of temp file to final backup file
            Files.move(tempFile, backupFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            cleanOldBackups();
            logger.info("Created backup: " + backupFile.getFileName());
        } catch (IOException e) {
            logger.severe("Failed to create backup: " + e.getMessage());
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ex) {
                logger.warning("Failed to cleanup temporary backup file: " + ex.getMessage());
            }
        }
    }

    private void cleanOldBackups() {
        try {
            int keepCount = Math.max(1, plugin.getConfig().getInt("backup.keep-count", 5));
            List<Path> backups = new ArrayList<>();
            
            Files.list(backupDir)
                .filter(path -> path.toString().endsWith(".zip"))
                .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
                .forEach(backups::add);

            if (backups.size() > keepCount) {
                for (int i = keepCount; i < backups.size(); i++) {
                    try {
                        Files.delete(backups.get(i));
                    } catch (IOException e) {
                        logger.warning("Failed to delete old backup " + backups.get(i) + ": " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            logger.warning("Failed to clean old backups: " + e.getMessage());
        }
    }

    private void setupBackupSchedule() {
        if (!plugin.getConfig().getBoolean("backup.enabled", true)) {
            return;
        }

        int interval = Math.max(1, plugin.getConfig().getInt("backup.interval-hours", DEFAULT_BACKUP_INTERVAL));
        scheduler.scheduleAtFixedRate(this::createBackup, 1, interval, TimeUnit.HOURS);
    }

    public void recordPackUsage(String packName, Player player, boolean success, long loadTime) {
        if (isShutdown || !plugin.getConfig().getBoolean("statistics.enabled", true)) {
            return;
        }

        if (packName == null || packName.isEmpty()) {
            logger.warning("Attempted to record usage for null or empty pack name");
            return;
        }

        PackStatistics stats = packStats.computeIfAbsent(packName, key -> new PackStatistics());
        stats.recordUsage(success, loadTime);

        if (success && plugin.getConfig().getBoolean("statistics.save-preferences", true)) {
            recordPlayerPreference(player.getUniqueId(), packName);
        }

        saveStatistics();
    }

    private void recordPlayerPreference(UUID playerId, String packName) {
        if (playerId == null || packName == null || packName.isEmpty()) {
            return;
        }

        List<String> preferences = playerPreferences.computeIfAbsent(playerId, key -> new ArrayList<>());
        preferences.remove(packName);
        preferences.add(0, packName);

        int maxPreferences = Math.max(1, plugin.getConfig().getInt("statistics.max-preferences", DEFAULT_MAX_PREFERENCES));
        if (preferences.size() > maxPreferences) {
            preferences.subList(maxPreferences, preferences.size()).clear();
        }

        savePreferences();
    }

    public List<String> getPlayerPreferences(UUID playerId) {
        if (playerId == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(playerPreferences.getOrDefault(playerId, new ArrayList<>()));
    }

    private void loadStatistics() {
        if (!Files.exists(statsFile)) {
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(statsFile.toFile());
            for (String packName : config.getKeys(false)) {
                if (packName != null && !packName.isEmpty()) {
                    PackStatistics stats = new PackStatistics();
                    stats.loadFromConfig(config.getConfigurationSection(packName));
                    packStats.put(packName, stats);
                }
            }
        } catch (Exception e) {
            logger.severe("Failed to load statistics: " + e.getMessage());
            // Create backup of corrupted file
            try {
                Files.copy(statsFile, statsFile.resolveSibling("statistics.yml.bak"), 
                          StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                logger.severe("Failed to backup corrupted statistics file: " + ex.getMessage());
            }
        }
    }

    private void saveStatistics() {
        if (isShutdown) return;

        try {
            Path tempFile = statsFile.resolveSibling("statistics.yml.tmp");
            YamlConfiguration config = new YamlConfiguration();
            
            for (Map.Entry<String, PackStatistics> entry : packStats.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    entry.getValue().saveToConfig(config.createSection(entry.getKey()));
                }
            }
            
            config.save(tempFile.toFile());
            Files.move(tempFile, statsFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.severe("Failed to save statistics: " + e.getMessage());
        }
    }

    private void loadPreferences() {
        if (!Files.exists(preferencesFile)) {
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(preferencesFile.toFile());
            for (String uuidStr : config.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(uuidStr);
                    List<String> prefs = config.getStringList(uuidStr);
                    if (!prefs.isEmpty()) {
                        playerPreferences.put(playerId, new ArrayList<>(prefs));
                    }
                } catch (IllegalArgumentException e) {
                    logger.warning("Invalid UUID in preferences file: " + uuidStr);
                }
            }
        } catch (Exception e) {
            logger.severe("Failed to load preferences: " + e.getMessage());
            try {
                Files.copy(preferencesFile, preferencesFile.resolveSibling("preferences.yml.bak"),
                          StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                logger.severe("Failed to backup corrupted preferences file: " + ex.getMessage());
            }
        }
    }

    private void savePreferences() {
        if (isShutdown) return;

        try {
            Path tempFile = preferencesFile.resolveSibling("preferences.yml.tmp");
            YamlConfiguration config = new YamlConfiguration();
            
            for (Map.Entry<UUID, List<String>> entry : playerPreferences.entrySet()) {
                if (entry.getKey() != null && !entry.getValue().isEmpty()) {
                    config.set(entry.getKey().toString(), entry.getValue());
                }
            }
            
            config.save(tempFile.toFile());
            Files.move(tempFile, preferencesFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.severe("Failed to save preferences: " + e.getMessage());
        }
    }

    public void shutdown() {
        isShutdown = true;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(SHUTDOWN_TIMEOUT, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Final save of data
        saveStatistics();
        savePreferences();
    }

    public Map<String, PackStatistics> getPackStats() {
        return Collections.unmodifiableMap(packStats);
    }

    public boolean isShutdown() {
        return isShutdown;
    }
} 