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

public class LoadCommand implements CommandExecutor, TabCompleter {
    private final Resourceloader plugin;

    public LoadCommand(Resourceloader plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageManager().getMessage("general.players-only"));
            return true;
        }

        if (!player.hasPermission("resourceloader.load")) {
            player.sendMessage(plugin.getMessageManager().getMessage("general.no-permission"));
            return true;
        }

        if (args.length < 1) {
            String defaultPackPath = plugin.getConfig().getString("server-pack");
            if (defaultPackPath == null || defaultPackPath.isEmpty()) {
                player.sendMessage(plugin.getMessageManager().getMessage("resource-packs.no-default"));
                return true;
            }

            plugin.getPackManager().loadResourcePack(player, "server", defaultPackPath);
            return true;
        }

        String packName = args[0].toLowerCase();
        if (!plugin.getResourcePacks().containsKey(packName)) {
            player.sendMessage(plugin.getMessageManager().formatMessage("resource-packs.not-found", 
                "pack", packName));
            return true;
        }

        String packPath = plugin.getConfig().getConfigurationSection("resource-packs").getString(packName);
        plugin.getPackManager().loadResourcePack(player, packName, packPath);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1 && sender.hasPermission("resourceloader.load")) {
            List<String> packs = new ArrayList<>(plugin.getResourcePacks().keySet());
            StringUtil.copyPartialMatches(args[0], packs, completions);
        }

        Collections.sort(completions);
        return completions;
    }
} 