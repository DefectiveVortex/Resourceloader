package org.vortex.resourceloader.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.vortex.resourceloader.Resourceloader;

import java.io.FileNotFoundException;
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
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessageManager().getMessage("general.players-only"));
            return true;
        }
        Player player = (Player) sender;

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

            loadResourcePack(player, "server");
            return true;
        }

        String packName = plugin.getPackManager().findPackKey(args[0]);
        if (packName == null) {
            player.sendMessage(plugin.getMessageManager().formatMessage("resource-packs.not-found",
                    "pack", args[0]));
            return true;
        }

        loadResourcePack(player, packName);
        return true;
    }

    public void loadResourcePack(Player player, String packName) {
        String packPath = plugin.getPackManager().resolvePackPath(packName);
        if (packPath == null || packPath.isEmpty()) {
            player.sendMessage(plugin.getMessageManager().formatMessage("resource-packs.invalid-path",
                    "pack", packName));
            return;
        }

        player.sendMessage(plugin.getMessageManager().formatMessage("resource-packs.loading", "pack", packName));
        plugin.getPackManager().sendPack(player, packName, false).exceptionally(e -> {
            if (e instanceof FileNotFoundException) {
                player.sendMessage(plugin.getMessageManager().getMessage("resource-packs.file-not-found"));
            } else {
                player.sendMessage(plugin.getMessageManager().formatMessage("resource-packs.load-failed",
                        "error", String.valueOf(e.getMessage())));
            }
            plugin.getLogger().warning("Failed to load pack '" + packName + "' for " + player.getName() + ": "
                    + e.getMessage());
            return null;
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            if (sender.hasPermission("resourceloader.load")) {
                List<String> packs = new ArrayList<>(plugin.getResourcePacks().keySet());
                StringUtil.copyPartialMatches(args[0], packs, completions);
            }
        }

        Collections.sort(completions);
        return completions;
    }
}
