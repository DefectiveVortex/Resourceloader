package org.vortex.resourceloader;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class ResourcePackEnforcer implements Listener {
    private final Resourceloader plugin;

    public ResourcePackEnforcer(Resourceloader plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Check if enforcement is enabled
        if (!plugin.getConfig().getBoolean("enforcement.enabled", false)) {
            return;
        }

        // Check if player has bypass permission
        if (player.hasPermission("resourceloader.bypass")) {
            return;
        }

        // Get the default server pack
        String serverPack = plugin.getConfig().getString("server-pack");
        if (serverPack == null || serverPack.isEmpty()) {
            plugin.getLogger().warning("Enforcement enabled but no default server pack configured!");
            return;
        }

        // Delay the resource pack sending slightly to ensure the client is ready
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    plugin.getLoadCommand().loadResourcePack(player, "server", serverPack);
                }
            }
        }.runTaskLater(plugin, 20L); // 1 second delay
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        if (!plugin.getConfig().getBoolean("enforcement.enabled", false)) {
            return;
        }

        Player player = event.getPlayer();
        
        // Check if player has bypass permission
        if (player.hasPermission("resourceloader.bypass")) {
            return;
        }

        switch (event.getStatus()) {
            case DECLINED:
                handleDeclined(player);
                break;
            case FAILED_DOWNLOAD:
                handleFailedDownload(player);
                break;
            case SUCCESSFULLY_LOADED:
                player.sendMessage(ChatColor.GREEN + "Resource pack loaded successfully!");
                break;
            case ACCEPTED:
                player.sendMessage(ChatColor.YELLOW + "Downloading resource pack...");
                break;
            case DOWNLOADED:
                player.sendMessage(ChatColor.YELLOW + "Resource pack downloaded, applying...");
                break;
            case FAILED_RELOAD:
                player.sendMessage(ChatColor.RED + "Failed to reload resource pack. Please try again.");
                break;
            case INVALID_URL:
                player.sendMessage(ChatColor.RED + "Invalid resource pack URL. Please contact an administrator.");
                break;
            case DISCARDED:
                handleDeclined(player); // Handle discarded same as declined since both are user rejections
                break;
        }
    }

    private void handleDeclined(Player player) {
        String action = plugin.getConfig().getString("enforcement.action", "kick").toLowerCase();
        String message = plugin.getConfig().getString("enforcement.decline-message",
                "You must accept the resource pack to play on this server!");

        if (action.equals("kick")) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    player.kickPlayer(ChatColor.RED + message);
                }
            }.runTask(plugin);
        } else if (action.equals("restrict")) {
            player.sendMessage(ChatColor.RED + message);
            // Additional restriction logic can be implemented here
            // For example, preventing movement, interaction, etc.
        }
    }

    private void handleFailedDownload(Player player) {
        String action = plugin.getConfig().getString("enforcement.failed-action", "kick").toLowerCase();
        String message = plugin.getConfig().getString("enforcement.failed-message",
                "Failed to download the resource pack. Please try joining again!");

        if (action.equals("kick")) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    player.kickPlayer(ChatColor.RED + message);
                }
            }.runTask(plugin);
        } else if (action.equals("restrict")) {
            player.sendMessage(ChatColor.RED + message);
            // Optionally retry the download
            String serverPack = plugin.getConfig().getString("server-pack");
            if (serverPack != null && !serverPack.isEmpty()) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isOnline()) {
                            plugin.getLoadCommand().loadResourcePack(player, "server", serverPack);
                        }
                    }
                }.runTaskLater(plugin, 100L); // 5 second delay before retry
            }
        }
    }
} 