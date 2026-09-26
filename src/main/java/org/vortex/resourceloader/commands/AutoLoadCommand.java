package org.vortex.resourceloader.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.vortex.resourceloader.Resourceloader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AutoLoadCommand implements CommandExecutor, TabCompleter {
    private final Resourceloader plugin;

    public AutoLoadCommand(Resourceloader plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessageManager().getMessage("general.players-only"));
            return true;
        }
        Player player = (Player) sender;

        if (!player.hasPermission("resourceloader.autoload")) {
            sender.sendMessage(plugin.getMessageManager().getMessage("general.no-permission"));
            return true;
        }

        if (args.length == 0) {
            List<String> preferences = plugin.getPackManager().getPlayerPreferences(player.getUniqueId());
            if (preferences.isEmpty()) {
                player.sendMessage(plugin.getMessageManager().getMessage("autoload.no-preference"));
            } else {
                player.sendMessage(plugin.getMessageManager().formatMessage("autoload.current-preference",
                    "pack", preferences.get(0)));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("clear")) {
            plugin.getPackManager().clearPlayerPreferences(player.getUniqueId());
            player.sendMessage(plugin.getMessageManager().getMessage("autoload.cleared"));
            return true;
        }

        String packName = plugin.getPackManager().findPackKey(args[0]);
        if (packName == null) {
            player.sendMessage(plugin.getMessageManager().formatMessage("general.invalid-pack",
                "pack", args[0]));
            return true;
        }

        // Set preference and immediately apply the pack (local file or URL)
        plugin.getPackManager().setPlayerPreference(player.getUniqueId(), packName);
        player.sendMessage(plugin.getMessageManager().formatMessage("autoload.set", "pack", packName));

        plugin.getPackManager().sendPack(player, packName, false).exceptionally(e -> {
            player.sendMessage(plugin.getMessageManager().formatMessage("autoload.set-failed",
                "pack", packName, "error", String.valueOf(e.getMessage())));
            plugin.getLogger().warning("Failed to apply resource pack for " + player.getName() + ": " + e.getMessage());
            return null;
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            if (sender.hasPermission("resourceloader.autoload")) {
                List<String> options = new ArrayList<>(plugin.getResourcePacks().keySet());
                options.add("clear");
                StringUtil.copyPartialMatches(args[0], options, completions);
            }
        }

        Collections.sort(completions);
        return completions;
    }
}
