package org.vortex.resourceloader.core;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.vortex.resourceloader.Resourceloader;
import org.vortex.resourceloader.util.FileUtil;

import java.io.File;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.bukkit.configuration.file.YamlConfiguration;

public class ResourcePackManager {
    private final Resourceloader plugin;
    private final Logger logger;
    private final Map<String, File> resourcePacks;
    private final ResourcePackCache packCache;
    private final ResourcePackServer packServer;
    private final ResourcePackWatcher packWatcher;
    private final Map<UUID, List<String>> playerPreferences;
    private final Path preferencesFile;

    public ResourcePackManager(Resourceloader plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.resourcePacks = new HashMap<>();
        this.packCache = new ResourcePackCache(plugin);
        this.packServer = new ResourcePackServer(plugin);
        this.packWatcher = new ResourcePackWatcher(plugin, this);
        this.playerPreferences = new ConcurrentHashMap<>();
        this.preferencesFile = plugin.getDataFolder().toPath().resolve("preferences.yml");
        
        loadPreferences();
        loadResourcePacks(true);
        packServer.start();
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
            logger.warning("Failed to load player preferences: " + e.getMessage());
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
            logger.warning("Failed to save player preferences: " + e.getMessage());
        }
    }

    public List<String> getPlayerPreferences(UUID playerId) {
        return playerPreferences.getOrDefault(playerId, new ArrayList<>());
    }

    public void setPlayerPreference(UUID playerId, String packName) {
        List<String> preferences = playerPreferences.computeIfAbsent(playerId, k -> new ArrayList<>());
        preferences.remove(packName);
        preferences.add(0, packName);

        int maxPreferences = plugin.getConfig().getInt("statistics.max-preferences", 5);
        if (preferences.size() > maxPreferences) {
            preferences.subList(maxPreferences, preferences.size()).clear();
        }

        savePreferences();
    }

    public void clearPlayerPreferences(UUID playerId) {
        playerPreferences.remove(playerId);
        savePreferences();
    }

    public void loadResourcePacks(boolean silent) {
        resourcePacks.clear();
        File packDirectory = getResourcePackDirectory();
        
        if (!packDirectory.exists()) {
            packDirectory.mkdirs();
        }

        // Load default server pack
        String serverPack = plugin.getConfig().getString("server-pack");
        if (serverPack != null && !serverPack.isEmpty()) {
            File serverPackFile = new File(packDirectory, serverPack);
            if (serverPackFile.exists()) {
                resourcePacks.put("server", serverPackFile);
                if (!silent) {
                    logger.info("Loaded default server pack: " + serverPack);
                }
            }
        }

        // Load additional packs
        ConfigurationSection packs = plugin.getConfig().getConfigurationSection("resource-packs");
        if (packs != null) {
            for (String key : packs.getKeys(false)) {
                String packPath = packs.getString(key);
                if (packPath != null && !packPath.startsWith("http")) {
                    File packFile = new File(packDirectory, packPath);
                    if (packFile.exists()) {
                        resourcePacks.put(key, packFile);
                        if (!silent) {
                            logger.info("Loaded resource pack: " + key);
                        }
                    } else {
                        logger.warning("Resource pack file not found: " + packPath);
                    }
                } else if (packPath != null) {
                    resourcePacks.put(key, null);
                    if (!silent) {
                        logger.info("Registered external resource pack URL: " + key);
                    }
                }
            }
        }
    }

    public void loadResourcePack(Player player, String packName, String packPath) {
        if (packPath == null || packPath.isEmpty()) {
            player.sendMessage(plugin.getMessageManager().formatMessage("resource-packs.invalid-pack", 
                "pack", packName));
            return;
        }

        try {
            if (packPath.startsWith("http://") || packPath.startsWith("https://")) {
                player.sendMessage(plugin.getMessageManager().formatMessage("resource-packs.loading", 
                    "pack", packName));
                
                packCache.getCachedPack(packPath, packName)
                    .thenAccept(cachedFile -> {
                        try {
                            byte[] fileHash = FileUtil.calcSHA1(cachedFile);
                            String downloadUrl = packServer.createDownloadURL(player, packName, cachedFile.getName());
                            player.setResourcePack(downloadUrl, fileHash);
                            player.sendMessage(plugin.getMessageManager().getMessage("resource-packs.load-success"));
                        } catch (Exception e) {
                            player.sendMessage(plugin.getMessageManager().formatMessage("resource-packs.load-failed", 
                                "error", e.getMessage()));
                            logger.warning("Failed to load cached pack: " + e.getMessage());
                        }
                    })
                    .exceptionally(e -> {
                        player.sendMessage(plugin.getMessageManager().formatMessage("resource-packs.load-failed", 
                            "error", e.getMessage()));
                        logger.warning("Failed to load pack: " + e.getMessage());
                        return null;
                    });
            } else {
                File packFile = resourcePacks.get(packName);
                if (packFile == null || !packFile.exists()) {
                    player.sendMessage(plugin.getMessageManager().getMessage("resource-packs.invalid-pack"));
                    return;
                }
                String finalUrl = packServer.createDownloadURL(player, packName, packPath);
                byte[] hash = FileUtil.calcSHA1(packFile);

                if (hash != null) {
                    player.setResourcePack(finalUrl, hash);
                } else {
                    player.setResourcePack(finalUrl);
                }

                player.sendMessage(plugin.getMessageManager().formatMessage("resource-packs.loading", 
                    "pack", packName));
            }

            logger.info("Resource pack '" + packName + "' load attempted for player " + player.getName());
        } catch (Exception e) {
            player.sendMessage(plugin.getMessageManager().formatMessage("resource-packs.load-failed", 
                "error", e.getMessage()));
            logger.warning("Resource pack loading failed: " + e.getMessage());
        }
    }

    private File getResourcePackDirectory() {
        String customPath = plugin.getConfig().getString("storage.resource-pack-directory", "");
        if (!customPath.isEmpty()) {
            File customDir = new File(customPath);
            if (customDir.exists() || customDir.mkdirs()) {
                return customDir;
            }
            logger.warning("Failed to use custom resource pack directory: " + customPath);
        }
        return new File(plugin.getDataFolder(), "packs");
    }

    public Map<String, File> getResourcePacks() {
        return resourcePacks;
    }

    public ResourcePackCache getPackCache() {
        return packCache;
    }

    public ResourcePackServer getPackServer() {
        return packServer;
    }

    public void shutdown() {
        packServer.stop();
        packWatcher.shutdown();
        savePreferences();
    }

    private String sanitizePackName(String packName) {
        // Remove file extension if present
        if (packName.toLowerCase().endsWith(".zip")) {
            packName = packName.substring(0, packName.length() - 4);
        }
        
        // First, convert to lowercase for consistency
        packName = packName.toLowerCase();
        
        // Replace spaces, periods and special characters with underscores
        packName = packName.replaceAll("[\\s.]+", "_");
        
        // Remove any other non-alphanumeric characters except underscores
        packName = packName.replaceAll("[^a-z0-9_]", "");
        
        // Remove multiple consecutive underscores
        packName = packName.replaceAll("_+", "_");
        
        // Remove leading and trailing underscores
        packName = packName.replaceAll("^_+|_+$", "");
        
        // If name is empty after sanitization, use a default
        if (packName.isEmpty()) {
            packName = "resource_pack";
        }
        
        return packName;
    }

    public void handleNewResourcePack(File packFile) {
        if (!packFile.getName().toLowerCase().endsWith(".zip")) {
            return;
        }

        // Reload config to avoid wiping unrelated sections
        plugin.reloadConfig();

        String originalName = packFile.getName();
        String sanitizedName = sanitizePackName(originalName);
        File packDirectory = getResourcePackDirectory();
        File newFile = new File(packDirectory, sanitizedName + ".zip");

        try {
            // Check if the file is already in the correct format
            if (packFile.getName().equals(sanitizedName + ".zip") && packFile.getParentFile().equals(packDirectory)) {
                newFile = packFile;
            } else {
                // Find a unique name if needed
                int counter = 1;
                while (newFile.exists()) {
                    String nextName = sanitizedName + "_" + counter;
                    if (!isPackNameInConfig(nextName)) {
                        newFile = new File(packDirectory, nextName + ".zip");
                        break;
                    }
                    counter++;
                }
                Files.move(packFile.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            // Get or create the resource-packs section
            ConfigurationSection packs = plugin.getConfig().getConfigurationSection("resource-packs");
            if (packs == null) {
                packs = plugin.getConfig().createSection("resource-packs");
            }

            // Remove any old entry for the original file name
            String oldKey = null;
            for (String key : packs.getKeys(false)) {
                String value = packs.getString(key);
                if (value != null && value.equals(originalName)) {
                    oldKey = key;
                    break;
                }
            }
            if (oldKey != null) {
                packs.set(oldKey, null);
                resourcePacks.remove(oldKey);
            }

            // Generate a unique key for the config
            String configKey = getUniqueConfigKey(sanitizedName, packs);
            packs.set(configKey, newFile.getName());
            resourcePacks.put(configKey, newFile);

            plugin.saveConfig();
            logger.info("Added new resource pack: " + configKey + " (file: " + newFile.getName() + ")");
        } catch (IOException e) {
            logger.warning("Failed to process new resource pack " + packFile.getName() + ": " + e.getMessage());
        }
    }

    private boolean isPackNameInConfig(String name) {
        ConfigurationSection packs = plugin.getConfig().getConfigurationSection("resource-packs");
        if (packs == null) {
            return false;
        }
        for (String key : packs.getKeys(false)) {
            String packPath = packs.getString(key);
            if (packPath != null && packPath.equals(name + ".zip")) {
                return true;
            }
        }
        return false;
    }

    private String getUniqueConfigKey(String baseName, ConfigurationSection packs) {
        String key = baseName;
        int counter = 1;
        
        while (packs.contains(key)) {
            key = baseName + "_" + counter;
            counter++;
        }
        
        return key;
    }
}