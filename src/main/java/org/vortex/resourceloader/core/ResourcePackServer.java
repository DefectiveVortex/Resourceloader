package org.vortex.resourceloader.core;

import com.sun.net.httpserver.HttpServer;
import org.bukkit.entity.Player;
import org.vortex.resourceloader.Resourceloader;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class ResourcePackServer {
    private final Resourceloader plugin;
    private final Logger logger;
    private HttpServer server;
    private final Map<UUID, String> playerTokens;

    public ResourcePackServer(Resourceloader plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.playerTokens = new HashMap<>();
    }

    public void start() {
        try {
            int port = plugin.getConfig().getInt("server-port", 40021);
            server = HttpServer.create(new InetSocketAddress(port), 0);
            
            // Secure endpoint for authenticated downloads
            server.createContext("/download", exchange -> {
                String query = exchange.getRequestURI().getQuery();
                if (query == null || !query.contains("token=")) {
                    exchange.sendResponseHeaders(403, -1);
                    return;
                }

                String token = query.substring(query.indexOf("token=") + 6);
                String packPath = null;
                UUID playerId = null;

                for (Map.Entry<UUID, String> entry : playerTokens.entrySet()) {
                    if (entry.getValue().equals(token)) {
                        playerId = entry.getKey();
                        break;
                    }
                }

                if (playerId != null) {
                    packPath = exchange.getRequestURI().getPath().substring("/download/".length());
                    playerTokens.remove(playerId);
                }

                if (packPath == null) {
                    exchange.sendResponseHeaders(403, -1);
                    return;
                }

                serveResourcePack(exchange, packPath);
            });

            // Public endpoint for vanilla Minecraft client
            server.createContext("/public", exchange -> {
                if (!plugin.getConfig().getBoolean("enforcement.make-pack-public", false)) {
                    exchange.sendResponseHeaders(403, -1);
                    return;
                }

                String packPath = exchange.getRequestURI().getPath().substring("/public/".length());
                serveResourcePack(exchange, packPath);
            });

            server.setExecutor(null);
            server.start();
            logger.info("Resource pack server started on port " + port);
        } catch (IOException e) {
            logger.severe("Failed to start resource pack server: " + e.getMessage());
        }
    }

    private void serveResourcePack(com.sun.net.httpserver.HttpExchange exchange, String packPath) throws IOException {
        File packFile = new File(plugin.getDataFolder(), "packs/" + packPath);
        
        if (!packFile.exists()) {
            packFile = new File(plugin.getDataFolder(), "cache/" + packPath);
        }

        if (!packFile.exists()) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", "application/zip");
        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=31536000");
        exchange.sendResponseHeaders(200, packFile.length());

        try (OutputStream os = exchange.getResponseBody()) {
            Files.copy(packFile.toPath(), os);
            plugin.getLogger().info("Successfully served resource pack: " + packPath);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            logger.info("Resource pack server stopped");
        }
    }

    public String createDownloadURL(Player player, String packName, String packPath) {
        String host = plugin.getConfig().getString("server-host", "localhost");
        int port = plugin.getConfig().getInt("server-port", 40021);
        
        if (plugin.getConfig().getBoolean("enforcement.use-server-properties", false) &&
            plugin.getConfig().getBoolean("enforcement.make-pack-public", false)) {
            return String.format("http://%s:%d/public/%s", host, port, packPath);
        }

        String token = UUID.randomUUID().toString();
        playerTokens.put(player.getUniqueId(), token);
        return String.format("http://%s:%d/download/%s?token=%s", host, port, packPath, token);
    }
}