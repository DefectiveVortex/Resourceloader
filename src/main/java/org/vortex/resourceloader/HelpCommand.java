package org.vortex.resourceloader;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class HelpCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(ChatColor.YELLOW + "=== ResourceLoader Help ===");
        
        // Basic commands (available to all players)
        if (sender.hasPermission("resourceloader.load")) {
            sender.sendMessage(ChatColor.GOLD + "/load " + ChatColor.WHITE + "- Load the default server resource pack");
            sender.sendMessage(ChatColor.GOLD + "/load <packname> " + ChatColor.WHITE + "- Load a specific resource pack");
        }
        
        if (sender.hasPermission("resourceloader.list")) {
            sender.sendMessage(ChatColor.GOLD + "/listpacks " + ChatColor.WHITE + "- List all available resource packs");
        }

        // Admin commands
        if (sender.hasPermission("resourceloader.admin")) {
            sender.sendMessage(ChatColor.RED + "\nAdmin Commands:");
            sender.sendMessage(ChatColor.GOLD + "/mergepack <output>.zip <pack1> <pack2> [pack3...] " + ChatColor.WHITE + 
                             "- Merge multiple resource packs into one");
            sender.sendMessage(ChatColor.GOLD + "/removepack <packname> " + ChatColor.WHITE + 
                             "- Remove a resource pack and its config entry");
            sender.sendMessage(ChatColor.GOLD + "/resourcereload " + ChatColor.WHITE + 
                             "- Reload the plugin configuration and packs");
            sender.sendMessage(ChatColor.GOLD + "/resourceversion " + ChatColor.WHITE + 
                             "- Check plugin version and updates");
            sender.sendMessage(ChatColor.GOLD + "/clearcache " + ChatColor.WHITE + 
                             "- Clear the resource pack cache");
        }

        // Show permissions info
        sender.sendMessage(ChatColor.YELLOW + "\nPermissions:");
        if (sender.hasPermission("resourceloader.admin")) {
            sender.sendMessage(ChatColor.GRAY + "You have admin access to all commands");
        } else if (sender.hasPermission("resourceloader.load")) {
            sender.sendMessage(ChatColor.GRAY + "You can use basic resource pack commands");
        }
        
        if (sender.hasPermission("resourceloader.bypass")) {
            sender.sendMessage(ChatColor.GRAY + "You can bypass forced resource packs");
        }

        return true;
    }
} 