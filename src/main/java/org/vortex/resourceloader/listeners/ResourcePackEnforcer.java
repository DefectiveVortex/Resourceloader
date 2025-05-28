package org.vortex.resourceloader.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.scheduler.BukkitTask;
import org.vortex.resourceloader.Resourceloader;
import org.vortex.resourceloader.util.FileUtil;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ResourcePackEnforcer implements Listener {
    private final Resourceloader plugin;
    private final Map<UUID, Integer> retryAttempts;
    private final Map<UUID, BukkitTask> pendingRetries;
    private final Set<UUID> restrictedPlayers;
    private static final String[] RESTRICTED_COMMANDS = {
        "tp", "teleport", "spawn", "home", "warp", "tpa",
        "sethome", "setwarp", "back", "return"
    };

    public ResourcePackEnforcer(Resourceloader plugin) {
        this.plugin = plugin;
        this.retryAttempts = new ConcurrentHashMap<>();
        this.pendingRetries = new ConcurrentHashMap<>();
        this.restrictedPlayers = ConcurrentHashMap.newKeySet();
        
        // Register additional events
        plugin.getServer().getPluginManager().registerEvents(new RestrictedModeHandler(), plugin);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            return;
        }

        Player player = event.getPlayer();
        if (player.hasPermission("resourceloader.bypass")) {
            return;
        }

        // Check for autoload preference first
        List<String> preferences = plugin.getPackManager().getPlayerPreferences(player.getUniqueId());
        if (!preferences.isEmpty()) {
            String preferredPack = preferences.get(0);
            File packFile = plugin.getResourcePacks().get(preferredPack);
            if (packFile != null && packFile.exists()) {
                try {
                    String downloadUrl = plugin.getPackManager().getPackServer().createDownloadURL(player, preferredPack, packFile.getName());
                    byte[] hash = FileUtil.calcSHA1(packFile);
                    player.setResourcePack(downloadUrl, hash);
                    return;
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load autoload pack for " + player.getName() + ": " + e.getMessage());
                }
            }
        }

        // If no autoload or it failed, check for enforced pack
        String serverPack = plugin.getConfig().getString("server-pack");
        if (serverPack != null && !serverPack.isEmpty()) {
            try {
                if (serverPack.startsWith("http")) {
                    player.setResourcePack(serverPack);
                } else {
                    File packFile = new File(plugin.getDataFolder(), "packs/" + serverPack);
                    if (packFile.exists()) {
                        String downloadUrl = plugin.getPackManager().getPackServer().createDownloadURL(player, "server", serverPack);
                        byte[] hash = FileUtil.calcSHA1(packFile);
                        player.setResourcePack(downloadUrl, hash);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to enforce resource pack for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Check if enforcement is enabled
        if (!plugin.getConfig().getBoolean("enforcement.enabled", false)) {
            // If not enforced, check for autoload preference
            loadPreferredPack(player);
            return;
        }

        // Skip if player has bypass permission
        if (player.hasPermission("resourceloader.bypass")) {
            return;
        }

        // Get default server pack
        String serverPack = plugin.getConfig().getString("server-pack");
        if (serverPack == null || serverPack.isEmpty()) {
            return;
        }

        // Reset retry attempts and cleanup for new join
        cleanup(player.getUniqueId());

        // Load the server pack
        plugin.getPackManager().loadResourcePack(player, "server", serverPack);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cleanup(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) {
            // Clean up all pending tasks and restrictions
            pendingRetries.values().forEach(BukkitTask::cancel);
            pendingRetries.clear();
            retryAttempts.clear();
            restrictedPlayers.clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        
        if (player.hasPermission("resourceloader.bypass")) {
            return;
        }

        switch (event.getStatus()) {
            case DECLINED:
                if (plugin.getConfig().getBoolean("enforce.kick-on-decline", true)) {
                    player.kickPlayer(plugin.getMessageManager().getMessage("enforcement.declined"));
                }
                break;
            case FAILED_DOWNLOAD:
                if (plugin.getConfig().getBoolean("enforce.kick-on-fail", true)) {
                    player.kickPlayer(plugin.getMessageManager().getMessage("enforcement.failed"));
                }
                break;
            default:
                break;
        }
    }

    private void cleanup(UUID playerId) {
        retryAttempts.remove(playerId);
        BukkitTask task = pendingRetries.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        restrictedPlayers.remove(playerId);
    }

    private void loadPreferredPack(Player player) {
        if (!player.hasPermission("resourceloader.autoload")) {
            return;
        }

        plugin.getPackManager().getPlayerPreferences(player.getUniqueId())
            .stream()
            .findFirst()
            .ifPresent(packName -> {
                if (plugin.getResourcePacks().containsKey(packName)) {
                    String packPath = plugin.getConfig().getConfigurationSection("resource-packs")
                        .getString(packName);
                    if (packPath != null && !packPath.isEmpty()) {
                        plugin.getPackManager().loadResourcePack(player, packName, packPath);
                        player.sendMessage(plugin.getMessageManager().formatMessage("autoload.loaded", 
                            "pack", packName));
                    }
                }
            });
    }

    private class RestrictedModeHandler implements Listener {
        @EventHandler
        public void onPlayerCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
            if (restrictedPlayers.contains(event.getPlayer().getUniqueId())) {
                String command = event.getMessage().substring(1).split(" ")[0].toLowerCase();
                for (String restricted : RESTRICTED_COMMANDS) {
                    if (command.equals(restricted)) {
                        event.setCancelled(true);
                        event.getPlayer().sendMessage(plugin.getMessageManager().getMessage("enforcement.restricted-command"));
                        return;
                    }
                }
            }
        }

        @EventHandler
        public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent event) {
            if (restrictedPlayers.contains(event.getPlayer().getUniqueId()) &&
                plugin.getConfig().getBoolean("enforcement.restrict.prevent-interaction", true)) {
                event.setCancelled(true);
            }
        }
    }
} 