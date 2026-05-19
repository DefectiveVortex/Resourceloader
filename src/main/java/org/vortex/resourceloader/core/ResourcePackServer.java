package org.vortex.resourceloader.core;

import com.sun.net.httpserver.HttpServer;
import org.bukkit.entity.Player;
import org.vortex.resourceloader.Resourceloader;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ResourcePackServer {
    private final Resourceloader plugin;
    private final Logger logger;
    private HttpServer server;
    private final Map<UUID, String> playerTokens;

    public ResourcePackServer(Resourceloader plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.playerTokens = new ConcurrentHashMap<>();
    }

    public void start() {
        try {
            int port = plugin.getConfig().getInt("server.port", 40021);
            // Bind to 0.0.0.0 by default to allow external connections, or use specific
            // bind address if needed
            // For now, we bind to wildcard to ensure it's accessible
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

                try {
                    serveResourcePack(exchange, packPath);
                } catch (SecurityException e) {
                    logger.warning("Blocked potential path traversal attempt from " + exchange.getRemoteAddress());
                    exchange.sendResponseHeaders(403, -1);
                }
            });

            // Public endpoint for vanilla Minecraft client
            server.createContext("/public", exchange -> {
                if (!plugin.getConfig().getBoolean("enforcement.make-pack-public", false)) {
                    exchange.sendResponseHeaders(403, -1);
                    return;
                }

                String packPath = exchange.getRequestURI().getPath().substring("/public/".length());
                try {
                    serveResourcePack(exchange, packPath);
                } catch (SecurityException e) {
                    logger.warning("Blocked potential path traversal attempt from " + exchange.getRemoteAddress());
                    exchange.sendResponseHeaders(403, -1);
                }
            });

            server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(16));
            server.start();

            String publicAddress = resolvePublicHost();

            logger.info("Resource pack server started on port " + port);
            logger.info("Public URL base: http://" + publicAddress + ":" + port);

        } catch (IOException e) {
            logger.severe("Failed to start resource pack server: " + e.getMessage());
        }
    }

    private void serveResourcePack(com.sun.net.httpserver.HttpExchange exchange, String packPath) throws IOException {
        File dataFolder = plugin.getDataFolder();
        File packsDir = plugin.getPackManager().getResolvedResourcePackDirectory();
        File cacheDir = new File(dataFolder, "cache");
        Path packsRoot = packsDir.getCanonicalFile().toPath().normalize();
        Path cacheRoot = cacheDir.getCanonicalFile().toPath().normalize();
        File packFile = new File(packsDir, packPath);

        if (!packFile.exists()) {
            packFile = new File(cacheDir, packPath);
        }

        Path canonicalPackPath = packFile.getCanonicalFile().toPath().normalize();

        if (!canonicalPackPath.startsWith(packsRoot) && !canonicalPackPath.startsWith(cacheRoot)) {
            throw new SecurityException("Path traversal attempt");
        }

        if (!packFile.exists()) {
            logger.warning("Requested resource pack not found: " + packPath);
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        try {
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=31536000");
            exchange.sendResponseHeaders(200, packFile.length());

            try (OutputStream os = exchange.getResponseBody()) {
                Files.copy(packFile.toPath(), os);
            }
            // Only log on debug or success if needed, but avoid spamming for every chunk
            plugin.getLogger()
                    .info("Successfully served resource pack: " + packPath + " (" + packFile.length() + " bytes)");
        } catch (IOException e) {
            logger.warning("Failed to serve resource pack " + packPath + ": " + e.getMessage());
            throw e; // Re-throw to let the server handle the connection close
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            logger.info("Resource pack server stopped");
        }
    }

    public String createDownloadURL(Player player, String packName, String packPath) {
        String host = resolvePublicHost();

        int port = plugin.getConfig().getInt("server.port", 40021);
        String encodedPath = URLEncoder.encode(packPath, StandardCharsets.UTF_8).replace("+", "%20");

        if (plugin.getConfig().getBoolean("enforcement.use-server-properties", false) &&
                plugin.getConfig().getBoolean("enforcement.make-pack-public", false)) {
            return String.format("http://%s:%d/public/%s", host, port, encodedPath);
        }

        String token = UUID.randomUUID().toString();
        playerTokens.put(player.getUniqueId(), token);
        return String.format("http://%s:%d/download/%s?token=%s", host, port, encodedPath, token);
    }

    private String resolvePublicHost() {
        String configured = plugin.getConfig().getString("server.address", "");
        if (!configured.isBlank()) {
            return configured;
        }

        if (plugin.getConfig().getBoolean("server.localhost", false)) {
            return "localhost";
        }

        String serverIp = plugin.getServer().getIp();
        if (serverIp != null && !serverIp.isBlank() && !"0.0.0.0".equals(serverIp)) {
            return serverIp;
        }

        try {
            String detected = InetAddress.getLocalHost().getHostAddress();
            if (detected != null && !detected.isBlank()) {
                return detected;
            }
        } catch (IOException ignored) {
        }

        return plugin.getConfig().getString("server.fallback", "localhost");
    }
}
