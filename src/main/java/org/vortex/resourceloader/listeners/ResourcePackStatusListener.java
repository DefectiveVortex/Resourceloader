package org.vortex.resourceloader.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.vortex.resourceloader.Resourceloader;

public class ResourcePackStatusListener implements Listener {
    private final Resourceloader plugin;

    public ResourcePackStatusListener(Resourceloader plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        String playerName = event.getPlayer().getName();
        PlayerResourcePackStatusEvent.Status status = event.getStatus();

        switch (status) {
            case SUCCESSFULLY_LOADED:
                plugin.getLogger().info("Player " + playerName + " successfully loaded resource pack.");
                break;
            case FAILED_DOWNLOAD:
                plugin.getLogger().warning("Player " + playerName + " failed to download resource pack.");
                break;
            case DECLINED:
                // Kicking is ResourcePackEnforcer's job, and only for the enforced pack
                plugin.getLogger().warning("Player " + playerName + " declined resource pack.");
                break;
            case ACCEPTED:
                plugin.getLogger().info("Player " + playerName + " accepted resource pack download.");
                break;
            default:
                plugin.getLogger().info("Player " + playerName + " resource pack status: " + status);
                break;
        }
    }
}
