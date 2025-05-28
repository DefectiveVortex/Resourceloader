package org.vortex.resourceloader.core;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.vortex.resourceloader.Resourceloader;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class ResourcePackWatcher {
    private final Resourceloader plugin;
    private final Logger logger;
    private final ResourcePackManager packManager;
    private final WatchService watchService;
    private final ScheduledExecutorService scheduler;
    private final Object configLock = new Object();
    private volatile boolean running = true;
    private final Set<String> processedEvents;
    private static final long EVENT_DEBOUNCE_TIME = 2000; // 2 seconds

    public ResourcePackWatcher(Resourceloader plugin, ResourcePackManager packManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.packManager = packManager;
        this.processedEvents = ConcurrentHashMap.newKeySet();
        
        WatchService service = null;
        try {
            service = FileSystems.getDefault().newWatchService();
            File packDir = new File(plugin.getDataFolder(), "packs");
            if (!packDir.exists()) {
                packDir.mkdirs();
            }
            Path packPath = packDir.toPath();
            packPath.register(service, 
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);
        } catch (IOException e) {
            logger.severe("Failed to initialize resource pack watcher: " + e.getMessage());
        }
        this.watchService = service;

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ResourcePackWatcher");
            t.setDaemon(true);
            return t;
        });

        if (plugin.getConfig().getBoolean("storage.auto-detection", true)) {
            startWatching();
        }
    }

    private void startWatching() {
        if (watchService == null) return;

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                if (!running) return;

                WatchKey key = watchService.poll();
                if (key == null) return;

                boolean needsSync = false;
                Set<String> currentEvents = new HashSet<>();

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path fileName = ev.context();

                    if (fileName.toString().toLowerCase().endsWith(".zip")) {
                        String eventKey = fileName.toString() + "_" + kind.name();
                        if (!processedEvents.contains(eventKey)) {
                            handlePackChange(kind, fileName);
                            currentEvents.add(eventKey);
                            needsSync = true;
                        }
                    }
                }

                if (needsSync) {
                    syncWithConfig(false);
                    // Schedule cleanup of processed events
                    scheduler.schedule(() -> 
                        processedEvents.removeAll(currentEvents), 
                        EVENT_DEBOUNCE_TIME, TimeUnit.MILLISECONDS);
                    processedEvents.addAll(currentEvents);
                }

                key.reset();
            } catch (Exception e) {
                if (running) {
                    logger.warning("Error in resource pack watcher: " + e.getMessage());
                }
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void handlePackChange(WatchEvent.Kind<?> kind, Path path) {
        String packName = path.getFileName().toString();
        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
            logger.info("New resource pack detected: " + packName);
        } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            logger.info("Resource pack removed: " + packName);
        } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
            logger.info("Resource pack modified: " + packName);
        }
    }

    public void syncWithConfig(boolean silent) {
        synchronized (configLock) {
            FileConfiguration config = plugin.getConfig();
            ConfigurationSection packs = config.getConfigurationSection("resource-packs");
            if (packs == null) {
                packs = config.createSection("resource-packs");
            }

            // Get existing pack mappings
            Map<String, String> existingPacks = new HashMap<>();
            for (String key : packs.getKeys(false)) {
                String packPath = packs.getString(key);
                if (packPath != null) {
                    existingPacks.put(packPath, key);
                }
            }

            // Check for new or modified files
            File packDirectory = new File(plugin.getDataFolder(), "packs");
            if (packDirectory.exists()) {
                File[] files = packDirectory.listFiles((dir, name) -> name.toLowerCase().endsWith(".zip"));
                if (files != null) {
                    for (File file : files) {
                        String fileName = file.getName();
                        
                        // If pack isn't in config or mapping has changed, add/update it
                        if (!existingPacks.containsKey(fileName)) {
                            // Generate a safe config key from the filename
                            String configKey = fileName;
                            // Remove .zip extension
                            if (configKey.toLowerCase().endsWith(".zip")) {
                                configKey = configKey.substring(0, configKey.length() - 4);
                            }
                            // Replace special characters and spaces with underscores
                            configKey = configKey.replaceAll("[^a-zA-Z0-9-_]", "_").toLowerCase();
                            
                            // Ensure unique key
                            String baseKey = configKey;
                            int counter = 1;
                            while (packs.contains(configKey)) {
                                configKey = baseKey + "_" + counter++;
                            }
                            
                            // Add to config
                            packs.set(configKey, fileName);
                            if (!silent) {
                                logger.info("Added new pack to config: " + fileName);
                            }
                        }
                    }
                }
            }

            // Save config without overwriting other sections
            try {
                // Get the existing config file
                File configFile = new File(plugin.getDataFolder(), "config.yml");
                if (!configFile.exists()) {
                    plugin.saveDefaultConfig();
                }

                // Load the existing config
                YamlConfiguration existingConfig = YamlConfiguration.loadConfiguration(configFile);
                
                // Update only the resource-packs section
                existingConfig.set("resource-packs", packs);
                
                // Save the config with all sections preserved
                existingConfig.save(configFile);
                
                // Reload the config in memory
                plugin.reloadConfig();
                
                // Reload packs
                packManager.loadResourcePacks(silent);
            } catch (Exception e) {
                logger.warning("Failed to sync config with pack directory: " + e.getMessage());
            }
        }
    }

    public void shutdown() {
        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            watchService.close();
        } catch (Exception e) {
            logger.warning("Error shutting down resource pack watcher: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
} 