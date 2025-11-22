package org.vortex.resourceloader.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.vortex.resourceloader.Resourceloader;
import org.vortex.resourceloader.util.FileUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class LoadCommand implements CommandExecutor, TabCompleter {
    private final Resourceloader plugin;

    public LoadCommand(Resourceloader plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageManager().getMessage("general.player-only"));
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

            loadResourcePack(player, "server", defaultPackPath);
            return true;
        }

        String packName = args[0].toLowerCase();
        ConfigurationSection packs = plugin.getConfig().getConfigurationSection("resource-packs");

        if (!plugin.getResourcePacks().containsKey(packName)) {
            player.sendMessage(plugin.getMessageManager().formatMessage("resource-packs.not-found",
                    "pack", packName));
            return true;
        }

        String packPath = packs.getString(packName);
        loadResourcePack(player, packName, packPath);
        return true;
    }

    public void loadResourcePack(Player player, String packName, String packPath) {
        if (packPath == null || packPath.isEmpty()) {
            player.sendMessage(plugin.getMessageManager().formatMessage("resource-packs.invalid-path",
                    "pack", packName));
            return;
        }

        try {
            if (packPath.startsWith("http://") || packPath.startsWith("https://")) {
                player.sendMessage(plugin.getMessageManager().formatMessage("resource-packs.loading",
                        "pack", packName));

                plugin.getPackManager().getPackCache().getCachedPack(packPath, packName)
                        .thenAccept(cachedFile -> {
                            try {
                                byte[] fileHash = FileUtil.calcSHA1(cachedFile);
                                String downloadUrl = plugin.getPackManager().getPackServer().createDownloadURL(player,
                                        packName, cachedFile.getName());
                                sendPackToPlayer(player, downloadUrl, fileHash, packName);
                            } catch (Exception e) {
                                player.sendMessage(
                                        plugin.getMessageManager().formatMessage("resource-packs.load-failed",
                                                "error", e.getMessage()));
                                plugin.getLogger().warning("Failed to load cached pack: " + e.getMessage());
                            }
                        })
                        .exceptionally(e -> {
                            player.sendMessage(plugin.getMessageManager().formatMessage("resource-packs.load-failed",
                                    "error", e.getMessage()));
                            plugin.getLogger().warning("Failed to load pack: " + e.getMessage());
                            return null;
                        });
            } else {
                File packFile = plugin.getResourcePacks().get(packName);
                if (packFile == null || !packFile.exists()) {
                    player.sendMessage(plugin.getMessageManager().getMessage("resource-packs.file-not-found"));
                    return;
                }
                String finalUrl = plugin.getPackManager().getPackServer().createDownloadURL(player, packName, packPath);
                byte[] hash = FileUtil.calcSHA1(packFile);

                sendPackToPlayer(player, finalUrl, hash, packName);
            }

        } catch (Exception e) {
            player.sendMessage(plugin.getMessageManager().formatMessage("resource-packs.load-failed",
                    "error", e.getMessage()));
            plugin.getLogger().warning("Resource pack loading failed for " + packName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendPackToPlayer(Player player, String url, byte[] hash, String packName) {
        plugin.getLogger().info("Sending resource pack '" + packName + "' to player " + player.getName());
        plugin.getLogger().info("URL: " + url);

        try {
            // Modern 1.20.3+ API with UUID and prompt
            // We use reflection or check version if we want to be strictly safe, but since
            // we updated api-version to 1.21
            // we can try to use the modern method if available, or fallback.
            // For now, standard setResourcePack is fine, but we should be aware of the
            // prompt message support.

            // In 1.20.3+, setResourcePack(UUID, String, byte[], String, boolean) exists.
            // But since we are compiling against a potentially older API jar (implied by
            // context), we stick to standard.
            // However, we will log explicitly.

            if (hash != null) {
                player.setResourcePack(url, hash);
            } else {
                player.setResourcePack(url);
            }

            player.sendMessage(plugin.getMessageManager().formatMessage("resource-packs.loading", "pack", packName));

        } catch (Exception e) {
            plugin.getLogger()
                    .severe("Failed to invoke setResourcePack for " + player.getName() + ": " + e.getMessage());
            player.sendMessage(plugin.getMessageManager().formatMessage("resource-packs.load-failed", "error",
                    "Internal error sending pack"));
        }
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