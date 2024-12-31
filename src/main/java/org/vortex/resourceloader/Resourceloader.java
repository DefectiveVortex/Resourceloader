// ResourceLoader.java
package org.vortex.resourceloader;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.TabCompleter;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.bukkit.util.StringUtil;
import org.vortex.resourceloader.util.FileUtil;
import java.util.Collections;

public final class Resourceloader extends JavaPlugin {
    private ResourcePackServer packServer;
    private final Map<String, File> resourcePacks = new HashMap<>();

    @Override
    public void onEnable() {
        // Save default config and setup folders
        saveDefaultConfig();
        setupResourcePackFolder();
        loadResourcePacks();

        // Initialize and start the resource pack server
        packServer = new ResourcePackServer(this);
        packServer.start();

        VersionChecker versionChecker = new VersionChecker(this);
        getServer().getPluginManager().registerEvents(versionChecker, this);
        getCommand("resourceversion").setExecutor(versionChecker.new VersionCommand());
        // Register merge command
        MergeCommand mergeCommand = new MergeCommand(this);
        getCommand("mergepack").setExecutor(mergeCommand);
        getCommand("mergepack").setTabCompleter(mergeCommand);
        // Register commands
        LoadCommand loadCommand = new LoadCommand(this);
        getCommand("load").setExecutor(loadCommand);
        getCommand("load").setTabCompleter(loadCommand);
        getCommand("resourcereload").setExecutor(new ResourceReloadCommand(this));
        getCommand("listpacks").setExecutor(new ListPacksCommand(this));

        getLogger().info("ResourceLoader has been enabled!");
    }

    @Override
    public void onDisable() {
        if (packServer != null) {
            packServer.stop();
        }
        getLogger().info("ResourceLoader has been disabled!");
    }

    private void setupResourcePackFolder() {
        File packFolder = new File(getDataFolder(), "packs");
        if (!packFolder.exists()) {
            packFolder.mkdirs();
        }
    }

    public void loadResourcePacks() {
        resourcePacks.clear();

        // Load default server pack
        String serverPack = getConfig().getString("server-pack");
        if (serverPack != null && !serverPack.isEmpty()) {
            File serverPackFile = new File(getDataFolder(), "packs" + File.separator + serverPack);
            if (serverPackFile.exists()) {
                resourcePacks.put("server", serverPackFile);
                getLogger().info("Loaded default server pack: " + serverPack);
            }
        }

        // Load additional packs
        ConfigurationSection packs = getConfig().getConfigurationSection("resource-packs");
        if (packs != null) {
            for (String key : packs.getKeys(false)) {
                String packPath = packs.getString(key);
                if (packPath != null && !packPath.startsWith("http")) {
                    File packFile = new File(getDataFolder(), "packs" + File.separator + packPath);
                    if (packFile.exists()) {
                        resourcePacks.put(key, packFile);
                        getLogger().info("Loaded resource pack: " + key);
                    } else {
                        getLogger().warning("Resource pack file not found: " + packPath);
                    }
                } else if (packPath != null) {
                    // For URL packs, we just store null as the file
                    resourcePacks.put(key, null);
                    getLogger().info("Registered external resource pack URL: " + key);
                }
            }
        }
    }

    public ResourcePackServer getPackServer() {
        return packServer;
    }

    public Map<String, File> getResourcePacks() {
        return resourcePacks;
    }

    public class LoadCommand implements CommandExecutor, TabCompleter {
        private final Resourceloader plugin;

        public LoadCommand(Resourceloader plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
                return true;
            }

            if (!player.hasPermission("resourceloader.load")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to load resource packs.");
                return true;
            }

            if (args.length < 1) {
                String defaultPackPath = plugin.getConfig().getString("server-pack");
                if (defaultPackPath == null || defaultPackPath.isEmpty()) {
                    player.sendMessage(ChatColor.RED + "No default server pack configured!");
                    return true;
                }

                loadResourcePack(player, "server", defaultPackPath);
                return true;
            }

            String packName = args[0].toLowerCase();
            ConfigurationSection packs = plugin.getConfig().getConfigurationSection("resource-packs");

            if (!resourcePacks.containsKey(packName)) {
                player.sendMessage(ChatColor.RED + "Resource pack '" + packName + "' not found!");
                return true;
            }

            String packPath = packs.getString(packName);
            loadResourcePack(player, packName, packPath);
            return true;
        }

        private void loadResourcePack(Player player, String packName, String packPath) {
            if (packPath == null || packPath.isEmpty()) {
                player.sendMessage(ChatColor.RED + "Invalid path configured for pack '" + packName + "'");
                return;
            }

            try {
                String finalUrl;
                byte[] hash = null;

                if (packPath.startsWith("http://") || packPath.startsWith("https://")) {
                    finalUrl = packPath;
                } else {
                    File packFile = plugin.getResourcePacks().get(packName);
                    if (packFile == null || !packFile.exists()) {
                        player.sendMessage(ChatColor.RED + "Resource pack file not found!");
                        return;
                    }
                    finalUrl = plugin.getPackServer().createDownloadURL(player, packName, packPath);
                    hash = FileUtil.calcSHA1(packFile);
                }

                // Use the hash if available
                if (hash != null) {
                    player.setResourcePack(finalUrl, hash);
                } else {
                    player.setResourcePack(finalUrl);
                }

                player.sendMessage(ChatColor.GREEN + "Loading the " + packName + " resource pack...");
                plugin.getLogger().info("Resource pack '" + packName + "' load attempted for player " + player.getName());
            } catch (Exception e) {
                player.sendMessage(ChatColor.RED + "Failed to load resource pack: " + e.getMessage());
                plugin.getLogger().warning("Resource pack loading failed: " + e.getMessage());
            }
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            List<String> completions = new ArrayList<>();

            if (args.length == 1) {
                if (sender.hasPermission("resourceloader.load")) {
                    List<String> packs = new ArrayList<>(resourcePacks.keySet());
                    StringUtil.copyPartialMatches(args[0], packs, completions);
                }
            }

            Collections.sort(completions);
            return completions;
        }
    }

    public class ListPacksCommand implements CommandExecutor {
        private final Resourceloader plugin;

        public ListPacksCommand(Resourceloader plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("resourceloader.list")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to list resource packs.");
                return true;
            }

            sender.sendMessage(ChatColor.YELLOW + "Available Resource Packs:");

            String serverPack = plugin.getConfig().getString("server-pack");
            if (serverPack != null && !serverPack.isEmpty()) {
                String packType = serverPack.startsWith("http") ? "URL" : "File";
                sender.sendMessage(ChatColor.GREEN + "Default Server Pack: " + ChatColor.GRAY + "(/load) [" + packType + "]");
            }

            for (Map.Entry<String, File> entry : resourcePacks.entrySet()) {
                String packName = entry.getKey();
                String packType = entry.getValue() == null ? "URL" : "File";
                if (!packName.equals("server")) {
                    sender.sendMessage(ChatColor.GRAY + "- " + packName + " (/load " + packName + ") [" + packType + "]");
                }
            }

            return true;
        }
    }

    public class ResourceReloadCommand implements CommandExecutor {
        private final Resourceloader plugin;

        public ResourceReloadCommand(Resourceloader plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("resourceloader.admin")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to reload the ResourceLoader plugin.");
                return true;
            }

            try {
                plugin.reloadConfig();
                plugin.loadResourcePacks();
                sender.sendMessage(ChatColor.GREEN + "ResourceLoader configuration reloaded successfully!");
                plugin.getLogger().info("Plugin configuration reloaded by " + sender.getName());
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Failed to reload configuration: " + e.getMessage());
                plugin.getLogger().warning("Configuration reload failed: " + e.getMessage());
            }

            return true;
        }
    }
}