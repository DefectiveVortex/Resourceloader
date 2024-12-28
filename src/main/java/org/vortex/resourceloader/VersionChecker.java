package org.vortex.resourceloader;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

public class VersionChecker implements Listener {
    private final Resourceloader plugin;
    private String latestVersion = null;
    private boolean isUpdateAvailable = false;

    public VersionChecker(Resourceloader plugin) {
        this.plugin = plugin;
        checkForUpdates();
    }

    private void checkForUpdates() {
        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL("https://api.github.com/repos/DefectiveVortex/Resourceloader/releases/latest");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONParser parser = new JSONParser();
                    JSONObject jsonResponse = (JSONObject) parser.parse(response.toString());
                    latestVersion = ((String) jsonResponse.get("tag_name")).replace("v", "");

                    String currentVersion = plugin.getDescription().getVersion();
                    isUpdateAvailable = !currentVersion.equals(latestVersion);

                    if (isUpdateAvailable) {
                        plugin.getLogger().info("A new version of Resourceloader is available! Current: " +
                                currentVersion + ", Latest: " + latestVersion);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to check for updates: " + e.getMessage());
            }
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("resourceloader.admin") && isUpdateAvailable && latestVersion != null) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage(ChatColor.YELLOW + "[Resourceloader] " + ChatColor.WHITE +
                        "A new version is available! Current: " + ChatColor.RED +
                        plugin.getDescription().getVersion() + ChatColor.WHITE +
                        ", Latest: " + ChatColor.GREEN + latestVersion);
                player.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.WHITE +
                        "/resourceversion check" + ChatColor.YELLOW + " for more information.");
            }, 40L); // Delay of 2 seconds after join
        }
    }

    public class VersionCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("resourceloader.version")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to check the version.");
                return true;
            }

            String currentVersion = plugin.getDescription().getVersion();

            if (args.length > 0 && args[0].equalsIgnoreCase("check")) {
                sender.sendMessage(ChatColor.YELLOW + "Checking for updates...");

                CompletableFuture.runAsync(() -> {
                    checkForUpdates();

                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (latestVersion == null) {
                            sender.sendMessage(ChatColor.RED + "Failed to check for updates.");
                            return;
                        }

                        sender.sendMessage(ChatColor.YELLOW + "Current version: " + ChatColor.WHITE + currentVersion);
                        sender.sendMessage(ChatColor.YELLOW + "Latest version: " + ChatColor.WHITE + latestVersion);

                        if (isUpdateAvailable) {
                            sender.sendMessage(ChatColor.GREEN + "A new version is available!");
                            sender.sendMessage(ChatColor.YELLOW + "Download it from: " + ChatColor.WHITE +
                                    "https://github.com/YOUR_USERNAME/Resourceloader/releases/latest");
                        } else {
                            sender.sendMessage(ChatColor.GREEN + "You are running the latest version!");
                        }
                    });
                });
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Resourceloader v" + currentVersion);
                sender.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.WHITE +
                        "/resourceversion check" + ChatColor.YELLOW + " to check for updates.");
            }

            return true;
        }
    }
}