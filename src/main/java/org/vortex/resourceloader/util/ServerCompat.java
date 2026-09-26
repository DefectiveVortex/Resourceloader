package org.vortex.resourceloader.util;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.util.UUID;

/**
 * Resource pack API differences between the server versions the plugin runs on (1.13+).
 */
public final class ServerCompat {
    /** Packs with ids, stacking and a required flag: 1.20.3+. */
    public static final boolean PACK_IDS = hasMethod(Player.class, "setResourcePack",
        UUID.class, String.class, byte[].class, String.class, boolean.class);
    /** A prompt and required flag without ids: 1.18 - 1.20.2. */
    public static final boolean PACK_PROMPT = hasMethod(Player.class, "setResourcePack",
        String.class, byte[].class, String.class, boolean.class);
    /** Status events that say which pack they are about: 1.20.3+. */
    public static final boolean STATUS_IDS = hasMethod(PlayerResourcePackStatusEvent.class, "getID");

    private ServerCompat() {
    }

    private static boolean hasMethod(Class<?> type, String name, Class<?>... parameters) {
        try {
            type.getMethod(name, parameters);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Sends a pack with the best API the server has. Before 1.20.3 a client holds one server pack at a time,
     * so {@code stack} and {@code id} are ignored there and the pack replaces the current one.
     */
    public static void sendPack(Player player, UUID id, String url, byte[] hash, boolean force, boolean stack) {
        if (PACK_IDS) {
            if (stack) {
                player.addResourcePack(id, url, hash, null, force);
            } else {
                player.setResourcePack(id, url, hash, null, force);
            }
        } else if (PACK_PROMPT) {
            player.setResourcePack(url, hash, null, force);
        } else {
            player.setResourcePack(url, hash);
        }
    }

    /** The pack a status event is about, or null on servers whose events do not say. */
    public static UUID statusId(PlayerResourcePackStatusEvent event) {
        return STATUS_IDS ? event.getID() : null;
    }
}
