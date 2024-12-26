package org.vortex.resourceloader;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URL;

public final class Resourceloader extends JavaPlugin {
    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Register commands
        getCommand("load").setExecutor(new LoadCommand(this));
        getCommand("resourcereload").setExecutor(new ResourceReloadCommand(this));
        getCommand("listpacks").setExecutor(new ListPacksCommand(this));

        getLogger().info("Resourceloader has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Resourceloader has been disabled!");
    }

    public class LoadCommand implements CommandExecutor {
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

            // If no arguments, load the default server pack
            if (args.length < 1) {
                String defaultPackUrl = plugin.getConfig().getString("server-pack");
                if (defaultPackUrl == null || defaultPackUrl.isEmpty()) {
                    player.sendMessage(ChatColor.RED + "No default server pack configured!");
                    return true;
                }

                loadResourcePack(player, "server", defaultPackUrl);
                return true;
            }

            // Handle specific pack loading
            String packName = args[0].toLowerCase();
            ConfigurationSection packs = plugin.getConfig().getConfigurationSection("resource-packs");

            if (packs == null || !packs.contains(packName)) {
                player.sendMessage(ChatColor.RED + "Resource pack '" + packName + "' not found!");
                return true;
            }

            String resourcePackUrl = packs.getString(packName);
            loadResourcePack(player, packName, resourcePackUrl);
            return true;
        }

        private void loadResourcePack(Player player, String packName, String url) {
            if (url == null || url.isEmpty()) {
                player.sendMessage(ChatColor.RED + "Invalid URL configured for pack '" + packName + "'");
                return;
            }

            try {
                new URL(url);
                player.setResourcePack(url);
                player.sendMessage(ChatColor.GREEN + "Loading the " + packName + " resource pack ...");
                plugin.getLogger().info("Resource pack '" + packName + "' load attempted for player " + player.getName());
            } catch (Exception e) {
                player.sendMessage(ChatColor.RED + "Failed to load resource pack: Invalid URL or download error");
                plugin.getLogger().warning("Resource pack loading failed: " + e.getMessage());
            }
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

            // Show default server pack
            String serverPack = plugin.getConfig().getString("server-pack");
            if (serverPack != null && !serverPack.isEmpty()) {
                sender.sendMessage(ChatColor.GREEN + "Default Server Pack: " + ChatColor.GRAY + "(/load)");
            }

            // List additional packs
            ConfigurationSection packs = plugin.getConfig().getConfigurationSection("resource-packs");
            if (packs != null && !packs.getKeys(false).isEmpty()) {
                sender.sendMessage(ChatColor.GREEN + "Additional Packs:");
                for (String packName : packs.getKeys(false)) {
                    sender.sendMessage(ChatColor.GRAY + "- " + packName + " (/load " + packName + ")");
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
            if (!sender.hasPermission("resourceloader.reload")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to reload the Resourceloader plugin.");
                return true;
            }

            try {
                plugin.reloadConfig();
                sender.sendMessage(ChatColor.GREEN + "Resourceloader configuration reloaded successfully!");
                plugin.getLogger().info("Plugin configuration reloaded by " + sender.getName());
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Failed to reload configuration: " + e.getMessage());
                plugin.getLogger().warning("Configuration reload failed: " + e.getMessage());
            }

            return true;
        }
    }
}