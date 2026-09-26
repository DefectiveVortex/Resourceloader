package org.vortex.resourceloader.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fixes common hand-editing mistakes in config.yml.
 *
 * The most common one is listing packs at the top level of the file instead of
 * indenting them under "resource-packs:", which makes the plugin ignore them.
 */
public final class ConfigRepair {
    private static final String CONFIG_FILE = "config.yml";
    private static final String PACKS_SECTION = "resource-packs";

    private ConfigRepair() {
    }

    public static void repair(JavaPlugin plugin) {
        File configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!configFile.exists()) {
            return;
        }

        Logger logger = plugin.getLogger();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        Set<String> knownKeys = loadBundledTopLevelKeys(plugin);
        if (knownKeys.isEmpty()) {
            return;
        }

        List<String> moved = new ArrayList<>();
        for (String key : config.getKeys(false)) {
            if (knownKeys.contains(key) || config.isConfigurationSection(key)) {
                continue;
            }

            String value = config.getString(key);
            if (!looksLikePackReference(value)) {
                continue;
            }

            ConfigurationSection packs = config.getConfigurationSection(PACKS_SECTION);
            if (packs == null) {
                packs = config.createSection(PACKS_SECTION);
            }

            String existing = packs.getString(key);
            if (existing == null) {
                packs.set(key, value);
            } else if (!existing.equals(value)) {
                logger.warning("config.yml: '" + key + "' is defined both at the top level (\"" + value
                    + "\") and under resource-packs (\"" + existing + "\"). Keeping the resource-packs entry.");
            }
            config.set(key, null);
            moved.add(key);
        }

        if (!moved.isEmpty()) {
            logger.warning("config.yml: found resource pack entries outside the 'resource-packs:' section: "
                + String.join(", ", moved) + ".");
            logger.warning("Pack entries must be indented under 'resource-packs:', for example:");
            logger.warning("  resource-packs:");
            logger.warning("    " + moved.get(0) + ": \""
                + config.getString(PACKS_SECTION + "." + moved.get(0)) + "\"");
            save(plugin, configFile, config, moved.size());
        }

        warnAboutServerPack(logger, config);
    }

    private static void warnAboutServerPack(Logger logger, YamlConfiguration config) {
        if (!config.getBoolean("enforcement.enabled", false)) {
            return;
        }

        String serverPack = config.getString("server-pack");
        if (serverPack != null && !serverPack.isEmpty()) {
            return;
        }

        ConfigurationSection packs = config.getConfigurationSection(PACKS_SECTION);
        String available = packs == null || packs.getKeys(false).isEmpty()
            ? "(no packs configured)"
            : String.join(", ", packs.getKeys(false));
        logger.warning("config.yml: enforcement is enabled but 'server-pack' is empty, so players will not"
            + " be sent a pack when they join. Set server-pack to a single file name or URL,"
            + " e.g. the value of one of: " + available);
    }

    private static boolean looksLikePackReference(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        return lower.endsWith(".zip") || lower.startsWith("http://") || lower.startsWith("https://");
    }

    private static Set<String> loadBundledTopLevelKeys(JavaPlugin plugin) {
        try (InputStream stream = plugin.getResource(CONFIG_FILE)) {
            if (stream == null) {
                return Collections.emptySet();
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                Set<String> keys = new HashSet<>(YamlConfiguration.loadConfiguration(reader).getKeys(false));
                // resource-packs ships empty, so it is not present as a key in the bundled file
                keys.add(PACKS_SECTION);
                return keys;
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read bundled " + CONFIG_FILE + ".", e);
            return Collections.emptySet();
        }
    }

    private static void save(JavaPlugin plugin, File configFile, YamlConfiguration config, int movedCount) {
        try {
            File backupFile = new File(configFile.getParentFile(), CONFIG_FILE + ".pre-repair.bak");
            Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            config.save(configFile);
            plugin.getLogger().warning("Moved " + movedCount + " entr" + (movedCount == 1 ? "y" : "ies")
                + " into 'resource-packs:' automatically. Backup of the previous file: " + backupFile.getName());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not repair " + CONFIG_FILE
                + ". Please move the entries under 'resource-packs:' manually.", e);
        }
    }
}
