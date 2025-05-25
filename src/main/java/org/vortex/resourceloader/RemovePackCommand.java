package org.vortex.resourceloader;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.StringUtil;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RemovePackCommand implements CommandExecutor, TabCompleter {
    private final Resourceloader plugin;

    public RemovePackCommand(Resourceloader plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("resourceloader.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to remove resource packs.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /removepack <packname>");
            return true;
        }

        String packName = args[0].toLowerCase();
        FileConfiguration config = plugin.getConfig();
        
        // Check if it's the server pack
        if (packName.equals("server")) {
            String serverPack = config.getString("server-pack");
            if (serverPack != null && !serverPack.startsWith("http")) {
                File packFile = new File(plugin.getDataFolder(), "packs" + File.separator + serverPack);
                try {
                    if (Files.deleteIfExists(packFile.toPath())) {
                        config.set("server-pack", null);
                        plugin.saveConfig();
                        plugin.loadResourcePacks();
                        sender.sendMessage(ChatColor.GREEN + "Successfully removed server pack: " + serverPack);
                    } else {
                        sender.sendMessage(ChatColor.RED + "Server pack file not found: " + serverPack);
                    }
                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "Failed to remove server pack: " + e.getMessage());
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Server pack is either not set or is a URL.");
            }
            return true;
        }

        // Handle regular resource packs
        if (config.contains("resource-packs." + packName)) {
            String packPath = config.getString("resource-packs." + packName);
            if (packPath != null && !packPath.startsWith("http")) {
                File packFile = new File(plugin.getDataFolder(), "packs" + File.separator + packPath);
                try {
                    if (Files.deleteIfExists(packFile.toPath())) {
                        config.set("resource-packs." + packName, null);
                        plugin.saveConfig();
                        plugin.loadResourcePacks();
                        sender.sendMessage(ChatColor.GREEN + "Successfully removed resource pack: " + packPath);
                    } else {
                        sender.sendMessage(ChatColor.RED + "Resource pack file not found: " + packPath);
                    }
                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "Failed to remove resource pack: " + e.getMessage());
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Cannot remove URL-based resource packs using this command.");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Resource pack '" + packName + "' not found in configuration!");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            if (sender.hasPermission("resourceloader.admin")) {
                List<String> packs = new ArrayList<>(plugin.getResourcePacks().keySet());
                StringUtil.copyPartialMatches(args[0], packs, completions);
            }
        }
        
        Collections.sort(completions);
        return completions;
    }
} 