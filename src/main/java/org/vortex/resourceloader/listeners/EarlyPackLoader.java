package org.vortex.resourceloader.listeners;

import org.vortex.resourceloader.Resourceloader;
import org.vortex.resourceloader.util.FileUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class EarlyPackLoader {
    private final Resourceloader plugin;
    private final Path serverPropertiesPath;
    private String originalResourcePack;
    private String originalResourcePackSha1;
    private String originalResourcePackPrompt;
    private boolean originalResourcePackRequired;
    private boolean useServerProperties;

    public EarlyPackLoader(Resourceloader plugin) {
        this.plugin = plugin;
        this.serverPropertiesPath = plugin.getServer().getWorldContainer().toPath().resolve("server.properties");
        this.useServerProperties = plugin.getConfig().getBoolean("enforcement.use-server-properties", false);
        
        if (useServerProperties) {
            loadOriginalSettings();
            updateServerProperties();
        }
    }

    private void loadOriginalSettings() {
        try {
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(serverPropertiesPath)) {
                properties.load(input);
            }
            
            // Store original values
            originalResourcePack = properties.getProperty("resource-pack", "");
            originalResourcePackSha1 = properties.getProperty("resource-pack-sha1", "");
            originalResourcePackPrompt = properties.getProperty("resource-pack-prompt", "");
            originalResourcePackRequired = Boolean.parseBoolean(properties.getProperty("require-resource-pack", "false"));
            
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load server.properties: " + e.getMessage());
        }
    }

    private void updateServerProperties() {
        if (!plugin.getConfig().getBoolean("enforcement.enabled", false)) {
            // If enforcement is disabled and we previously set a pack, restore original settings
            if (isOurResourcePack()) {
                restoreOriginalSettings();
            }
            return;
        }

        String serverPack = plugin.getConfig().getString("server-pack");
        if (serverPack == null || serverPack.isEmpty()) {
            return;
        }

        try {
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(serverPropertiesPath)) {
                properties.load(input);
            }

            // Prepare the new values
            String packUrl;
            String packSha1 = "";

            if (serverPack.startsWith("http")) {
                packUrl = serverPack;
            } else {
                File packFile = new File(plugin.getDataFolder(), "packs/" + serverPack);
                if (!packFile.exists()) {
                    plugin.getLogger().warning("Resource pack file not found: " + serverPack);
                    return;
                }

                if (plugin.getConfig().getBoolean("enforcement.make-pack-public", false)) {
                    // Create a public URL without authentication
                    String host = plugin.getConfig().getString("server-host", "localhost");
                    int port = plugin.getConfig().getInt("server-port", 40021);
                    packUrl = String.format("http://%s:%d/public/%s", host, port, serverPack);
                    
                    // We need to modify our ResourcePackServer to handle public URLs
                    plugin.getLogger().info("Using public URL for resource pack: " + packUrl);
                } else {
                    plugin.getLogger().warning("Cannot use local file with server.properties unless make-pack-public is enabled");
                    return;
                }
                
                // Calculate SHA1 for local files
                try {
                    byte[] hash = FileUtil.calcSHA1(packFile);
                    if (hash != null) {
                        packSha1 = bytesToHex(hash);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to calculate pack hash: " + e.getMessage());
                }
            }

            // Update properties
            properties.setProperty("resource-pack", packUrl);
            if (!packSha1.isEmpty()) {
                properties.setProperty("resource-pack-sha1", packSha1);
            }
            properties.setProperty("require-resource-pack", 
                String.valueOf(plugin.getConfig().getBoolean("enforce.kick-on-decline", true)));
            
            // Save properties while preserving comments
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = Files.newBufferedReader(serverPropertiesPath)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("resource-pack=")) {
                        line = "resource-pack=" + packUrl;
                    } else if (line.startsWith("resource-pack-sha1=")) {
                        line = "resource-pack-sha1=" + packSha1;
                    } else if (line.startsWith("require-resource-pack=")) {
                        line = "require-resource-pack=" + properties.getProperty("require-resource-pack");
                    }
                    lines.add(line);
                }
            }

            // Write back the file
            Files.write(serverPropertiesPath, lines, StandardCharsets.UTF_8);
            
            plugin.getLogger().info("Updated server.properties with resource pack settings");
            
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to update server.properties: " + e.getMessage());
        }
    }

    private boolean isOurResourcePack() {
        if (!useServerProperties) {
            return false;
        }
        
        try {
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(serverPropertiesPath)) {
                properties.load(input);
            }
            
            String currentPack = properties.getProperty("resource-pack", "");
            String serverPack = plugin.getConfig().getString("server-pack", "");
            
            return currentPack.contains(serverPack) || 
                   (serverPack.startsWith("http") && currentPack.equals(serverPack)) ||
                   (!serverPack.startsWith("http") && currentPack.endsWith(serverPack));
            
        } catch (IOException e) {
            return false;
        }
    }

    private void restoreOriginalSettings() {
        if (!useServerProperties) {
            return;
        }
        
        try {
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = Files.newBufferedReader(serverPropertiesPath)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("resource-pack=")) {
                        line = "resource-pack=" + originalResourcePack;
                    } else if (line.startsWith("resource-pack-sha1=")) {
                        line = "resource-pack-sha1=" + originalResourcePackSha1;
                    } else if (line.startsWith("resource-pack-prompt=")) {
                        line = "resource-pack-prompt=" + originalResourcePackPrompt;
                    } else if (line.startsWith("require-resource-pack=")) {
                        line = "require-resource-pack=" + originalResourcePackRequired;
                    }
                    lines.add(line);
                }
            }

            Files.write(serverPropertiesPath, lines, StandardCharsets.UTF_8);
            plugin.getLogger().info("Restored original resource pack settings in server.properties");
            
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to restore server.properties: " + e.getMessage());
        }
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public void cleanup() {
        if (useServerProperties && isOurResourcePack()) {
            restoreOriginalSettings();
        }
    }
} 