package org.vortex.resourceloader.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;
import org.vortex.resourceloader.Resourceloader;

import java.io.File;
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
            sender.sendMessage(plugin.getMessageManager().getMessage("general.no-permission"));
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("§cUsage: /removepack <packname>");
            return true;
        }

        String packName = args[0];
        if (!plugin.getResourcePacks().containsKey(packName)) {
            sender.sendMessage("§cResource pack '" + packName + "' not found!");
            return true;
        }

        File packFile = plugin.getResourcePacks().get(packName);
        if (packFile != null && packFile.exists()) {
            try {
                if (!packFile.delete()) {
                    sender.sendMessage("§cFailed to remove resource pack '" + packName + "'!");
                    return true;
                }
            } catch (SecurityException e) {
                sender.sendMessage("§cFailed to remove resource pack '" + packName + "'!");
                plugin.getLogger().warning("Failed to delete pack file: " + e.getMessage());
                return true;
            }
        }

        // Remove from config
        plugin.getConfig().getConfigurationSection("resource-packs").set(packName, null);
        plugin.saveConfig();

        // Remove from memory
        plugin.getResourcePacks().remove(packName);
        
        sender.sendMessage("§aSuccessfully removed resource pack '" + packName + "'!");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1 && sender.hasPermission("resourceloader.admin")) {
            List<String> packs = new ArrayList<>(plugin.getResourcePacks().keySet());
            StringUtil.copyPartialMatches(args[0], packs, completions);
        }
        
        Collections.sort(completions);
        return completions;
    }
} 