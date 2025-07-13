package org.vortex.resourceloader.core;

import org.vortex.resourceloader.Resourceloader;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Set;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ResourcePackWatcher {
    private final Resourceloader plugin;
    private final Logger logger;
    private final ResourcePackManager packManager;
    private WatchService watchService;
    private Thread watcherThread;
    private volatile boolean running = true;
    private final Set<String> processingFiles = ConcurrentHashMap.newKeySet();

    public ResourcePackWatcher(Resourceloader plugin, ResourcePackManager packManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.packManager = packManager;
        startWatcher();
    }

    private void startWatcher() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            File packsDir = new File(plugin.getDataFolder(), "packs");
            if (!packsDir.exists()) {
                packsDir.mkdirs();
            }

            Path packsPath = packsDir.toPath();
            packsPath.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);

            watcherThread = new Thread(() -> {
                while (running) {
                    try {
                        WatchKey key = watchService.take();
                        
                        // Use a small delay to avoid duplicate events
                        Thread.sleep(100);
                        
                        // Process all events at once
                        List<WatchEvent<?>> events = key.pollEvents();
                        for (WatchEvent<?> event : events) {
                            if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                                Path newPath = ((Path) key.watchable()).resolve((Path) event.context());
                                File newFile = newPath.toFile();
                                
                                if (newFile.getName().toLowerCase().endsWith(".zip") && 
                                    processingFiles.add(newFile.getName())) {
                                    // Small delay to ensure file is fully written
                                    Thread.sleep(500);
                                    
                                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                                        try {
                                            packManager.handleNewResourcePack(newFile);
                                        } finally {
                                            processingFiles.remove(newFile.getName());
                                        }
                                    });
                                }
                            }
                        }
                        
                        key.reset();
                    } catch (InterruptedException e) {
                        if (running) {
                            logger.warning("Resource pack watcher interrupted");
                        }
                        break;
                    } catch (Exception e) {
                        if (running) {
                            logger.warning("Error in resource pack watcher: " + e.getMessage());
                        }
                    }
                }
            }, "ResourcePackWatcher");
            
            watcherThread.setDaemon(true);
            watcherThread.start();
            
        } catch (IOException e) {
            logger.severe("Failed to start resource pack watcher: " + e.getMessage());
        }
    }

    public void shutdown() {
        running = false;
        if (watcherThread != null) {
            watcherThread.interrupt();
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                logger.warning("Error closing watch service: " + e.getMessage());
            }
        }
    }
}