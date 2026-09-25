package org.vortex.resourceloader.commands;

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
            sender.sendMessage(plugin.getMessageManager().getMessage("general.players-only"));
            return true;
        }

        if (!sender.hasPermission("resourceloader.admin")) {
            sender.sendMessage(plugin.getMessageManager().getMessage("general.no-permission"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(plugin.getMessageManager().getMessage("gui.usage"));
            return true;
        }

        String outputName = args[0];
        if (!outputName.toLowerCase().endsWith(".zip")) {
            outputName += ".zip";
        }

        if (!plugin.getConfig().getBoolean("gui.enabled", true)) {
            sender.sendMessage(plugin.getMessageManager().getMessage("gui.disabled"));
            return true;
        }

        Player player = (Player) sender;
        plugin.getMergeGUI().openMergeGUI(player, outputName);
        return true;
    }
} 