package org.vortex.resourceloader.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.vortex.resourceloader.Resourceloader;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ResourcePackEnforcer implements Listener {
    private final Resourceloader plugin;
    // Pack each player was sent on join while enforcement is on; only this pack's status can kick them
    private final Map<UUID, UUID> enforcedPacks;
    private final Set<UUID> restrictedPlayers;
    private static final String[] RESTRICTED_COMMANDS = {
        "tp", "teleport", "spawn", "home", "warp", "tpa",
        "sethome", "setwarp", "back", "return"
    };

    public ResourcePackEnforcer(Resourceloader plugin) {
        this.plugin = plugin;
        this.enforcedPacks = new ConcurrentHashMap<>();
        this.restrictedPlayers = ConcurrentHashMap.newKeySet();

        // Register additional events
        plugin.getServer().getPluginManager().registerEvents(new RestrictedModeHandler(), plugin);
    }

    private boolean enforcementActive() {
        return plugin.getConfig().getBoolean("enforcement.enabled", false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean enforce = enforcementActive() && !player.hasPermission("resourceloader.bypass");

        // Priority 1: the player's own autoload preference (bypass only skips forced packs, not this)
        List<String> preferences = plugin.getPackManager().getPlayerPreferences(player.getUniqueId());
        if (!preferences.isEmpty()) {
            String preferredPack = plugin.getPackManager().findPackKey(preferences.get(0));
            if (preferredPack != null) {
                plugin.getLogger().info("Loading autoload preference '" + preferredPack + "' for player " + player.getName());
                sendJoinPack(player, preferredPack, "Autoload", enforce);
                return;
            }
            plugin.getLogger().warning("Autoload pack '" + preferences.get(0) + "' of " + player.getName()
                + " no longer exists");
        }

        // Priority 2: the enforced server pack. With use-server-properties the vanilla server sends it.
        if (enforce && !plugin.getConfig().getBoolean("enforcement.use-server-properties", false)) {
            String serverPack = plugin.getConfig().getString("server-pack");
            if (serverPack != null && !serverPack.isEmpty()) {
                plugin.getLogger().info("Loading enforced server pack for player " + player.getName());
                sendJoinPack(player, "server", "Enforcement", true);
                return;
            }
            plugin.getLogger().warning("Server pack is not configured but enforcement is enabled");
        } else if (player.hasPermission("resourceloader.bypass") && enforcementActive()) {
            plugin.getLogger().info("Player " + player.getName() + " has bypass permission, skipping pack enforcement");
        }
    }

    private void sendJoinPack(Player player, String packName, String logPrefix, boolean enforce) {
        boolean required = enforce && plugin.getConfig().getBoolean("enforcement.kick-on-decline", true);
        if (enforce) {
            enforcedPacks.put(player.getUniqueId(), org.vortex.resourceloader.core.ResourcePackManager.packId(packName));
            restrictedPlayers.add(player.getUniqueId());
        }
        plugin.getPackManager().sendPack(player, packName, required).exceptionally(e -> {
            plugin.getLogger().warning(logPrefix + ": Failed to load pack '" + packName + "' for "
                + player.getName() + ": " + e.getMessage());
            // Nothing reached the client, so do not keep the player waiting on it
            enforcedPacks.remove(player.getUniqueId());
            restrictedPlayers.remove(player.getUniqueId());
            return null;
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        UUID enforcedId = enforcedPacks.get(player.getUniqueId());

        // Only the pack sent on join under enforcement is mandatory; packs a player
        // picks with /load or /autoload afterwards can be declined freely
        if (enforcedId == null || !enforcedId.equals(event.getID())) {
            return;
        }
        if (!enforcementActive() || player.hasPermission("resourceloader.bypass")) {
            release(player.getUniqueId());
            return;
        }

        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED, DISCARDED:
                release(player.getUniqueId());
                break;
            case DECLINED:
                release(player.getUniqueId());
                if (plugin.getConfig().getBoolean("enforcement.kick-on-decline", true)) {
                    player.kickPlayer(plugin.getMessageManager().getMessage("enforcement.declined"));
                }
                break;
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD:
                release(player.getUniqueId());
                if (plugin.getConfig().getBoolean("enforcement.kick-on-fail", true)) {
                    player.kickPlayer(plugin.getMessageManager().getMessage("enforcement.failed"));
                }
                break;
            default:
                break;
        }
    }

    private void release(UUID playerId) {
        enforcedPacks.remove(playerId);
        restrictedPlayers.remove(playerId);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        release(event.getPlayer().getUniqueId());
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
