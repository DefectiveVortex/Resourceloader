package org.vortex.resourceloader;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ResourcePackWatcher {
    private final Resourceloader plugin;
    private final Logger logger;
    private final Path packsDirectory;
    private WatchService watchService;
    private final Map<String, Long> lastModifiedTimes;

    public ResourcePackWatcher(Resourceloader plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.packsDirectory = new File(plugin.getDataFolder(), "packs").toPath();
        this.lastModifiedTimes = new HashMap<>();
        
        try {
            if (!Files.exists(packsDirectory)) {
                Files.createDirectories(packsDirectory);
            }
            
            // Initialize watch service
            watchService = FileSystems.getDefault().newWatchService();
            packsDirectory.register(watchService, 
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);
            
            // Start watching for changes
            startWatching();
            
            // Do initial sync silently
            syncWithConfig(true);
        } catch (IOException e) {
            logger.warning("Failed to initialize resource pack watcher: " + e.getMessage());
        }
    }

    private void startWatching() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    WatchKey key = watchService.poll();
                    if (key != null) {
                        handleWatchEvents(key);
                        key.reset();
                    }
                    
                    // Check for direct file modifications
                    checkForFileModifications();
                } catch (Exception e) {
                    logger.warning("Error in resource pack watcher: " + e.getMessage());
                }
            }
        }.runTaskTimerAsynchronously(plugin, 20L, 20L); // Check every second
    }

    private void handleWatchEvents(WatchKey key) {
        boolean needsSync = false;
        for (WatchEvent<?> event : key.pollEvents()) {
            WatchEvent.Kind<?> kind = event.kind();
            
            if (kind == StandardWatchEventKinds.OVERFLOW) {
                continue;
            }

            @SuppressWarnings("unchecked")
            WatchEvent<Path> ev = (WatchEvent<Path>) event;
            Path filename = ev.context();
            
            if (filename.toString().toLowerCase().endsWith(".zip")) {
                // Handle file renaming immediately for new files
                if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                    try {
                        Path fullPath = packsDirectory.resolve(filename);
                        if (Files.exists(fullPath) && filename.toString().contains(" ")) {
                            String newName = filename.toString().replace(" ", "_");
                            Path newPath = packsDirectory.resolve(newName);
                            if (!Files.exists(newPath)) {
                                Files.move(fullPath, newPath);
                                logger.info("Renamed new resource pack file: " + filename + " -> " + newName);
                            }
                        }
                    } catch (IOException e) {
                        logger.warning("Failed to rename file " + filename + ": " + e.getMessage());
                    }
                }
                needsSync = true;
            }
        }
        
        if (needsSync) {
            // Schedule sync on the main thread
            new BukkitRunnable() {
                @Override
                public void run() {
                    syncWithConfig();
                }
            }.runTask(plugin);
        }
    }

    private void checkForFileModifications() {
        try {
            AtomicBoolean needsSync = new AtomicBoolean(false);
            Set<String> currentFiles = new HashSet<>();
            
            Files.walk(packsDirectory, 1)
                .filter(path -> path.toString().toLowerCase().endsWith(".zip"))
                .forEach(path -> {
                    String fileName = path.getFileName().toString();
                    currentFiles.add(fileName);
                    long lastModified = path.toFile().lastModified();
                    
                    if (!lastModifiedTimes.containsKey(fileName) || 
                        lastModifiedTimes.get(fileName) != lastModified) {
                        needsSync.set(true);
                        lastModifiedTimes.put(fileName, lastModified);
                    }
                });
            
            // Check for deleted files
            if (lastModifiedTimes.keySet().retainAll(currentFiles)) {
                needsSync.set(true);
            }
            
            if (needsSync.get()) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        syncWithConfig();
                    }
                }.runTask(plugin);
            }
        } catch (IOException e) {
            logger.warning("Error checking for file modifications: " + e.getMessage());
        }
    }

    public void syncWithConfig() {
        syncWithConfig(false);
    }

    public void syncWithConfig(boolean silent) {
        try {
            // Get all ZIP files in the packs directory
            Set<String> packFiles = Files.walk(packsDirectory, 1)
                .filter(path -> path.toString().toLowerCase().endsWith(".zip"))
                .map(path -> {
                    String fileName = path.getFileName().toString();
                    // If file contains spaces, create a copy with underscores
                    if (fileName.contains(" ")) {
                        try {
                            String newName = fileName.replace(" ", "_");
                            Path newPath = path.resolveSibling(newName);
                            if (!Files.exists(newPath)) {
                                Files.copy(path, newPath);
                                Files.delete(path);
                                if (!silent) {
                                    logger.info("Renamed pack: " + fileName + " -> " + newName);
                                }
                            }
                            return newName;
                        } catch (IOException e) {
                            logger.warning("Failed to rename file " + fileName + ": " + e.getMessage());
                            return fileName;
                        }
                    }
                    return fileName;
                })
                .collect(Collectors.toSet());
            
            // Update config
            FileConfiguration config = plugin.getConfig();
            Map<String, String> resourcePacks = new HashMap<>();
            
            // Preserve existing mappings if they still exist
            if (config.contains("resource-packs")) {
                for (String key : config.getConfigurationSection("resource-packs").getKeys(false)) {
                    String value = config.getString("resource-packs." + key);
                    if (packFiles.contains(value)) {
                        resourcePacks.put(key, value);
                        packFiles.remove(value);
                    }
                }
            }
            
            // Add new files with generated keys
            for (String packFile : packFiles) {
                String key = generateKey(packFile, resourcePacks.keySet());
                resourcePacks.put(key, packFile);
            }
            
            // Update config
            config.set("resource-packs", null); // Clear existing section
            for (Map.Entry<String, String> entry : resourcePacks.entrySet()) {
                config.set("resource-packs." + entry.getKey(), entry.getValue());
            }
            
            // Save config
            plugin.saveConfig();
            
            // Reload resource packs in plugin
            new BukkitRunnable() {
                @Override
                public void run() {
                    plugin.loadResourcePacks(silent);
                }
            }.runTask(plugin);
        } catch (IOException e) {
            logger.warning("Failed to sync config with packs directory: " + e.getMessage());
        }
    }

    private String generateKey(String filename, Set<String> existingKeys) {
        // Remove .zip extension and convert to lowercase
        String baseKey = filename.toLowerCase().replace(".zip", "");
        
        // Replace non-alphanumeric characters with underscore
        baseKey = baseKey.replaceAll("[^a-z0-9]", "_");
        
        // Ensure the key is unique
        String key = baseKey;
        int counter = 1;
        while (existingKeys.contains(key)) {
            key = baseKey + "_" + counter++;
        }
        
        return key;
    }

    public void shutdown() {
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                logger.warning("Error closing watch service: " + e.getMessage());
            }
        }
    }
} 