package org.vortex.resourceloader.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.vortex.resourceloader.Resourceloader;

public class MergeGUICommand implements CommandExecutor {
    private final Resourceloader plugin;

    public MergeGUICommand(Resourceloader plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        if (!sender.hasPermission("resourceloader.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use the resource pack merger.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /mergegui <output_name>");
            return true;
        }

        String outputName = args[0];
        if (!outputName.toLowerCase().endsWith(".zip")) {
            outputName += ".zip";
        }

        if (!plugin.getConfig().getBoolean("gui.enabled", true)) {
            sender.sendMessage(ChatColor.RED + "The GUI feature is disabled in the configuration.");
            return true;
        }

        Player player = (Player) sender;
        plugin.getMergeGUI().openMergeGUI(player, outputName);
        return true;
    }
} 