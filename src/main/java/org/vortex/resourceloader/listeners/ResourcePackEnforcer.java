package org.vortex.resourceloader.listeners;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
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
    private final Map<UUID, Boolean> packLoading;
    private static final String[] RESTRICTED_COMMANDS = {
        "tp", "teleport", "spawn", "home", "warp", "tpa",
        "sethome", "setwarp", "back", "return"
    };

    public ResourcePackEnforcer(Resourceloader plugin) {
        this.plugin = plugin;
        this.retryAttempts = new ConcurrentHashMap<>();
        this.pendingRetries = new ConcurrentHashMap<>();
        this.restrictedPlayers = ConcurrentHashMap.newKeySet();
        this.packLoading = new ConcurrentHashMap<>();
        
        // Register additional events
        plugin.getServer().getPluginManager().registerEvents(new RestrictedModeHandler(), plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("resourceloader.bypass")) {
            plugin.getLogger().info("Player " + player.getName() + " has bypass permission, skipping pack loading");
            return;
        }

        // Priority 1: Check for autoload preference first
        List<String> preferences = plugin.getPackManager().getPlayerPreferences(player.getUniqueId());
        if (!preferences.isEmpty()) {
            String preferredPack = preferences.get(0);
            plugin.getLogger().info("Loading autoload preference '" + preferredPack + "' for player " + player.getName());
            tryLoadPack(player, preferredPack, true);
            return;
        }

        // Priority 2: Fall back to enforcement if no autoload preference
        if (plugin.getConfig().getBoolean("enforcement.enabled", false)) {
            String serverPack = plugin.getConfig().getString("server-pack");
            if (serverPack != null && !serverPack.isEmpty()) {
                plugin.getLogger().info("Loading enforced server pack for player " + player.getName());
                tryLoadPack(player, "server", false);
                return;
            }
        }
        
        plugin.getLogger().info("No autoload preference or enforcement configured for player " + player.getName());
    }

    private void tryLoadPack(Player player, String packName, boolean isAutoload) {
        try {
            String packPath;
            if (packName.equals("server")) {
                packPath = plugin.getConfig().getString("server-pack");
                if (packPath == null || packPath.isEmpty()) {
                    plugin.getLogger().warning("Server pack is not configured but enforcement is enabled");
                    return;
                }
            } else {
                ConfigurationSection packs = plugin.getConfig().getConfigurationSection("resource-packs");
                if (packs == null) {
                    plugin.getLogger().warning("No resource packs configured for autoload pack: " + packName);
                    return;
                }
                packPath = packs.getString(packName);
                if (packPath == null || packPath.isEmpty()) {
                    plugin.getLogger().warning("Pack '" + packName + "' not found in configuration for player " + player.getName());
                    return;
                }
            }

            String logPrefix = isAutoload ? "Autoload" : "Enforcement";
            plugin.getLogger().info(logPrefix + ": Attempting to load pack '" + packName + "' for player " + player.getName());

            if (packPath.startsWith("http")) {
                plugin.getLogger().info(logPrefix + ": Loading URL-based pack from " + packPath);
                plugin.getPackManager().getPackCache().getCachedPack(packPath, packName, player)
                    .thenAccept(cachedFile -> {
                        if (!player.isOnline()) return;
                        try {
                            byte[] hash = FileUtil.calcSHA1(cachedFile);
                            String downloadUrl = plugin.getPackManager().getPackServer()
                                .createDownloadURL(player, packName, cachedFile.getName());
                            
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                if (player.isOnline()) {
                                    if (hash != null) {
                                        player.setResourcePack(downloadUrl, hash);
                                        plugin.getLogger().info(logPrefix + ": Successfully sent pack '" + packName + "' to " + player.getName());
                                    } else {
                                        player.setResourcePack(downloadUrl);
                                        plugin.getLogger().info(logPrefix + ": Successfully sent pack '" + packName + "' to " + player.getName() + " (no hash)");
                                    }
                                }
                            });
                        } catch (Exception e) {
                            plugin.getLogger().warning(logPrefix + ": Failed to load pack '" + packName + "' for " + player.getName() + ": " + e.getMessage());
                        }
                    })
                    .exceptionally(e -> {
                        plugin.getLogger().warning(logPrefix + ": Failed to cache pack '" + packName + "' for " + player.getName() + ": " + e.getMessage());
                        return null;
                    });
            } else {
                File packFile = new File(plugin.getDataFolder(), "packs/" + packPath);
                if (packFile.exists()) {
                    plugin.getLogger().info(logPrefix + ": Loading local pack file: " + packFile.getName());
                    String downloadUrl = plugin.getPackManager().getPackServer()
                        .createDownloadURL(player, packName, packPath);
                    byte[] hash = FileUtil.calcSHA1(packFile);
                    
                    if (hash != null) {
                        player.setResourcePack(downloadUrl, hash);
                        plugin.getLogger().info(logPrefix + ": Successfully sent local pack '" + packName + "' to " + player.getName());
                    } else {
                        player.setResourcePack(downloadUrl);
                        plugin.getLogger().info(logPrefix + ": Successfully sent local pack '" + packName + "' to " + player.getName() + " (no hash)");
                    }
                } else {
                    plugin.getLogger().warning(logPrefix + ": Pack file not found: " + packFile.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            String logPrefix = isAutoload ? "Autoload" : "Enforcement";
            plugin.getLogger().warning(logPrefix + ": Failed to load pack '" + packName + "' for " + player.getName() + ": " + e.getMessage());
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
        packLoading.remove(playerId);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cleanup(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) {
            for (UUID playerId : pendingRetries.keySet()) {
                cleanup(playerId);
            }
        }
    }

    private class RestrictedModeHandler implements Listener {
        @EventHandler
        public void onPlayerCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
            if (restrictedPlayers.contains(event.getPlayer().getUniqueId())) {
                String command = event.getMessage().substring(1).split(" ")[0].toLowerCase();
                for (String restricted : RESTRICTED_COMMANDS) {
                    if (command.equals(restricted)) {
                        event.setCancelled(true);
                        event.getPlayer().sendMessage(plugin.getMessageManager()
                            .getMessage("enforcement.restricted-command"));
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