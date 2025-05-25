package org.vortex.resourceloader;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.entity.Player;

import java.io.*;
import java.net.InetSocketAddress;

public class ResourcePackServer {
    private final Resourceloader plugin;
    private HttpServer httpServer;

    public ResourcePackServer(Resourceloader plugin) {
        this.plugin = plugin;
    }

    public void start() {
        int port = plugin.getConfig().getInt("server-port", 40021);
        boolean isLocalhost = plugin.getConfig().getBoolean("localhost", false);

        try {
            InetSocketAddress bindAddress = isLocalhost ?
                    new InetSocketAddress("localhost", port) :
                    new InetSocketAddress(port);

            httpServer = HttpServer.create(bindAddress, 0);
            httpServer.createContext("/packs/", this::handlePackRequest);
            httpServer.setExecutor(null);
            httpServer.start();

            plugin.getLogger().info("Resource pack server started on port " + port +
                    (isLocalhost ? " (localhost only)" : " (all interfaces)"));
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to start HTTP server: " + e.getMessage());
        }
    }

    public String createDownloadURL(Player player, String packName, String packPath) {
        boolean isLocalhost = plugin.getConfig().getBoolean("localhost", false);
        String serverAddress;

        if (isLocalhost) {
            serverAddress = "localhost";
        } else {
            serverAddress = plugin.getConfig().getString("server-address");

            if (serverAddress == null || serverAddress.isEmpty()) {
                serverAddress = player.getServer().getIp();
                if (serverAddress == null || serverAddress.isEmpty() || serverAddress.equals("0.0.0.0")) {
                    serverAddress = plugin.getConfig().getString("fallback-address", "localhost");
                }
            }
        }

        return String.format("http://%s:%d/packs/%s",
                serverAddress,
                plugin.getConfig().getInt("server-port", 40021),
                packPath);
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            plugin.getLogger().info("Resource pack server stopped");
        }
    }

    private void handlePackRequest(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String[] pathParts = path.split("/");

        if (pathParts.length < 3) {
            sendError(exchange, 400, "Invalid request");
            return;
        }

        String packPath = pathParts[pathParts.length - 1];
        File packFile = new File(plugin.getDataFolder(), "packs" + File.separator + packPath);

        if (!packFile.exists() || !packFile.isFile()) {
            sendError(exchange, 404, "Resource pack not found");
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", "application/zip");
        exchange.getResponseHeaders().set("Content-Length", String.valueOf(packFile.length()));
        exchange.sendResponseHeaders(200, packFile.length());

        try (OutputStream os = exchange.getResponseBody();
             FileInputStream fis = new FileInputStream(packFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            os.flush();
        }
    }

    private void sendError(HttpExchange exchange, int code, String message) throws IOException {
        exchange.sendResponseHeaders(code, message.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(message.getBytes());
        }
    }
}
