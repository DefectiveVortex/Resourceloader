package org.vortex.resourceloader.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

public final class ConfigFileUpdater {
    private static final String[] MANAGED_RESOURCES = {
        "config.yml",
        "messages.yml"
    };

    private ConfigFileUpdater() {
    }

    public static void updateBundledConfigs(JavaPlugin plugin) {
        for (String resourceName : MANAGED_RESOURCES) {
            update(plugin, resourceName);
        }
    }

    public static YamlConfiguration update(JavaPlugin plugin, String resourceName) {
        return update(plugin, resourceName, new File(plugin.getDataFolder(), resourceName));
    }

    public static YamlConfiguration update(JavaPlugin plugin, String resourceName, File targetFile) {
        ensureFileExists(plugin, resourceName, targetFile);

        YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration(targetFile);
        YamlConfiguration defaultConfig = loadBundledDefaults(plugin, resourceName);
        if (defaultConfig == null) {
            return currentConfig;
        }

        int addedValues = addMissingDefaults(plugin, resourceName, currentConfig, defaultConfig);
        if (addedValues > 0) {
            saveWithBackup(plugin, resourceName, targetFile, currentConfig, addedValues);
        }

        return currentConfig;
    }

    private static void ensureFileExists(JavaPlugin plugin, String resourceName, File targetFile) {
        if (targetFile.exists()) {
            return;
        }

        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder for " + resourceName);
            return;
        }

        try {
            plugin.saveResource(resourceName, false);
            plugin.getLogger().info("Created " + resourceName + " from bundled defaults.");
        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.WARNING, "Bundled " + resourceName + " was not found.", e);
        }
    }

    private static YamlConfiguration loadBundledDefaults(JavaPlugin plugin, String resourceName) {
        try (InputStream defaultStream = plugin.getResource(resourceName)) {
            if (defaultStream == null) {
                plugin.getLogger().warning("Could not update " + resourceName + ": bundled defaults are missing.");
                return null;
            }

            try (InputStreamReader reader = new InputStreamReader(defaultStream, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read bundled " + resourceName + " defaults.", e);
            return null;
        }
    }

    private static int addMissingDefaults(JavaPlugin plugin, String resourceName,
                                          YamlConfiguration currentConfig,
                                          YamlConfiguration defaultConfig) {
        int added = 0;

        for (String path : defaultConfig.getKeys(true)) {
            if (defaultConfig.isConfigurationSection(path) || currentConfig.contains(path)) {
                continue;
            }

            String blockedParent = findNonSectionParent(currentConfig, path);
            if (blockedParent != null) {
                plugin.getLogger().warning("Skipped missing default " + resourceName + ":" + path
                    + " because existing key " + blockedParent + " is not a section.");
                continue;
            }

            currentConfig.set(path, defaultConfig.get(path));
            added++;
        }

        return added;
    }

    private static String findNonSectionParent(YamlConfiguration config, String path) {
        int separatorIndex = path.lastIndexOf('.');
        while (separatorIndex > 0) {
            String parentPath = path.substring(0, separatorIndex);
            if (config.contains(parentPath) && !config.isConfigurationSection(parentPath)) {
                return parentPath;
            }
            separatorIndex = parentPath.lastIndexOf('.');
        }
        return null;
    }

    private static void saveWithBackup(JavaPlugin plugin, String resourceName, File targetFile,
                                       YamlConfiguration currentConfig, int addedValues) {
        try {
            File backupFile = new File(targetFile.getParentFile(), resourceName + ".pre-update.bak");
            Files.copy(targetFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            currentConfig.save(targetFile);
            plugin.getLogger().info("Updated " + resourceName + " with " + addedValues
                + " missing value(s). Existing values were preserved. Backup: " + backupFile.getName());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not update " + resourceName + ".", e);
        }
    }
}
