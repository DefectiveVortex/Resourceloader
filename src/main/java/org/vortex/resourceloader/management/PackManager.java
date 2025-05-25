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

    public PackManager(Resourceloader plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.backupDir = plugin.getDataFolder().toPath().resolve("backups");
        this.statsFile = plugin.getDataFolder().toPath().resolve("statistics.yml");
        this.preferencesFile = plugin.getDataFolder().toPath().resolve("preferences.yml");
        this.scheduler = Executors.newScheduledThreadPool(1);
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
            logger.warning("Failed to initialize pack manager: " + e.getMessage());
        }
    }

    public void createBackup() {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path backupFile = backupDir.resolve("backup_" + timestamp + ".zip");
            
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(backupFile.toFile()))) {
                Path packsDir = plugin.getDataFolder().toPath().resolve("packs");
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

            cleanOldBackups();
            logger.info("Created backup: " + backupFile.getFileName());
        } catch (IOException e) {
            logger.warning("Failed to create backup: " + e.getMessage());
        }
    }

    private void cleanOldBackups() {
        try {
            int keepCount = plugin.getConfig().getInt("pack_management.backup.keep_count", 5);
            List<Path> backups = new ArrayList<>();
            
            Files.list(backupDir)
                .filter(path -> path.toString().endsWith(".zip"))
                .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
                .forEach(backups::add);

            if (backups.size() > keepCount) {
                for (int i = keepCount; i < backups.size(); i++) {
                    Files.delete(backups.get(i));
                }
            }
        } catch (IOException e) {
            logger.warning("Failed to clean old backups: " + e.getMessage());
        }
    }

    private void setupBackupSchedule() {
        if (!plugin.getConfig().getBoolean("pack_management.backup.enabled", true)) {
            return;
        }

        int interval = plugin.getConfig().getInt("pack_management.backup.interval", 24);
        scheduler.scheduleAtFixedRate(this::createBackup, 1, interval, TimeUnit.HOURS);
    }

    public void recordPackUsage(String packName, Player player, boolean success, long loadTime) {
        if (!plugin.getConfig().getBoolean("pack_management.statistics.enabled", true)) {
            return;
        }

        PackStatistics stats = packStats.computeIfAbsent(packName, _ -> new PackStatistics());
        stats.recordUsage(success, loadTime);

        if (success && plugin.getConfig().getBoolean("pack_management.player_preferences.save", true)) {
            recordPlayerPreference(player.getUniqueId(), packName);
        }

        saveStatistics();
    }

    private void recordPlayerPreference(UUID playerId, String packName) {
        List<String> preferences = playerPreferences.computeIfAbsent(playerId, _ -> new ArrayList<>());
        preferences.remove(packName);
        preferences.add(0, packName);

        int maxPreferences = plugin.getConfig().getInt("pack_management.player_preferences.remember_count", 5);
        if (preferences.size() > maxPreferences) {
            preferences.subList(maxPreferences, preferences.size()).clear();
        }

        savePreferences();
    }

    public List<String> getPlayerPreferences(UUID playerId) {
        return playerPreferences.getOrDefault(playerId, new ArrayList<>());
    }

    private void loadStatistics() {
        if (!Files.exists(statsFile)) {
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(statsFile.toFile());
            for (String packName : config.getKeys(false)) {
                PackStatistics stats = new PackStatistics();
                stats.loadFromConfig(config.getConfigurationSection(packName));
                packStats.put(packName, stats);
            }
        } catch (Exception e) {
            logger.warning("Failed to load statistics: " + e.getMessage());
        }
    }

    private void saveStatistics() {
        try {
            YamlConfiguration config = new YamlConfiguration();
            for (Map.Entry<String, PackStatistics> entry : packStats.entrySet()) {
                entry.getValue().saveToConfig(config.createSection(entry.getKey()));
            }
            config.save(statsFile.toFile());
        } catch (IOException e) {
            logger.warning("Failed to save statistics: " + e.getMessage());
        }
    }

    private void loadPreferences() {
        if (!Files.exists(preferencesFile)) {
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(preferencesFile.toFile());
            for (String uuidStr : config.getKeys(false)) {
                UUID playerId = UUID.fromString(uuidStr);
                playerPreferences.put(playerId, config.getStringList(uuidStr));
            }
        } catch (Exception e) {
            logger.warning("Failed to load preferences: " + e.getMessage());
        }
    }

    private void savePreferences() {
        try {
            YamlConfiguration config = new YamlConfiguration();
            for (Map.Entry<UUID, List<String>> entry : playerPreferences.entrySet()) {
                config.set(entry.getKey().toString(), entry.getValue());
            }
            config.save(preferencesFile.toFile());
        } catch (IOException e) {
            logger.warning("Failed to save preferences: " + e.getMessage());
        }
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        
        saveStatistics();
        savePreferences();
    }
} 