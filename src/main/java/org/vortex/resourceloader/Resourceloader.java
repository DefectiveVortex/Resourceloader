package org.vortex.resourceloader;

import com.sun.net.httpserver.HttpServer;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public final class Resourceloader extends JavaPlugin implements Listener {
    private final Map<String, String> packHashes = new HashMap<>();
    private HttpServer httpServer;
    private int port;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupResourcePackFolder();
        setupHttpServer();

        getCommand("load").setExecutor(new LoadCommand(this));
        getCommand("resourcereload").setExecutor(new ResourceReloadCommand(this));
        getCommand("listpacks").setExecutor(new ListPacksCommand(this));
        getCommand("autoload").setExecutor(new AutoloadCommand(this));

        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("Resourceloader has been enabled!");
    }

    private void setupResourcePackFolder() {
        File packFolder = new File(getDataFolder(), "packs");
        if (!packFolder.exists()) {
            packFolder.mkdirs();
        }
    }

    private void setupHttpServer() {
        try {
            httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            port = httpServer.getAddress().getPort();

            httpServer.createContext("/packs/", exchange -> {
                String requestPath = exchange.getRequestURI().getPath();
                String fileName = requestPath.substring("/packs/".length());
                File packFile = new File(getDataFolder(), "packs" + File.separator + fileName);

                if (packFile.exists() && packFile.isFile()) {
                    exchange.getResponseHeaders().set("Content-Type", "application/zip");
                    exchange.sendResponseHeaders(200, packFile.length());

                    try (OutputStream os = exchange.getResponseBody()) {
                        Files.copy(packFile.toPath(), os);
                    }
                } else {
                    String response = "Resource pack not found";
                    exchange.sendResponseHeaders(404, response.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                }
                exchange.close();
            });

            httpServer.setExecutor(null);
            httpServer.start();
            getLogger().info("Resource pack server started successfully");
        } catch (IOException e) {
            getLogger().severe("Failed to start HTTP server: " + e.getMessage());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String packName = getConfig().getString("player-packs." + player.getUniqueId());

        if (packName != null) {
            getServer().getScheduler().runTaskLater(this, () -> {
                if (player.isOnline()) {
                    String path;
                    if (packName.equals("server")) {
                        path = getConfig().getString("server-pack");
                    } else {
                        ConfigurationSection packs = getConfig().getConfigurationSection("resource-packs");
                        path = packs != null ? packs.getString(packName) : null;
                    }

                    if (path != null && !path.isEmpty()) {
                        loadResourcePack(player, packName, path);
                    }
                }
            }, 20L);
        }
    }

    private void loadResourcePack(Player player, String packName, String path) {
        if (path == null || path.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Invalid path configured for pack '" + packName + "'");
            return;
        }

        try {
            String finalUrl;
            if (path.startsWith("http://") || path.startsWith("https://")) {
                finalUrl = path;
            } else {
                finalUrl = String.format("http://localhost:%d/packs/%s", port, path);
            }

            player.setResourcePack(finalUrl);
            player.sendMessage(ChatColor.GREEN + "Loading the " + packName + " resource pack...");
            getLogger().info("Resource pack '" + packName + "' load attempted for player " + player.getName());
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Failed to load resource pack: " + e.getMessage());
            getLogger().warning("Resource pack loading failed: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
        getLogger().info("Resourceloader has been disabled!");
    }

    public class LoadCommand implements CommandExecutor {
        private final Resourceloader plugin;

        public LoadCommand(Resourceloader plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
                return true;
            }

            if (!player.hasPermission("resourceloader.load")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to load resource packs.");
                return true;
            }

            if (args.length < 1) {
                String defaultPackPath = plugin.getConfig().getString("server-pack");
                if (defaultPackPath == null || defaultPackPath.isEmpty()) {
                    player.sendMessage(ChatColor.RED + "No default server pack configured!");
                    return true;
                }

                plugin.loadResourcePack(player, "server", defaultPackPath);
                return true;
            }

            String packName = args[0].toLowerCase();
            ConfigurationSection packs = plugin.getConfig().getConfigurationSection("resource-packs");

            if (packs == null || !packs.contains(packName)) {
                player.sendMessage(ChatColor.RED + "Resource pack '" + packName + "' not found!");
                return true;
            }

            String resourcePackPath = packs.getString(packName);
            plugin.loadResourcePack(player, packName, resourcePackPath);
            return true;
        }
    }

    public class AutoloadCommand implements CommandExecutor {
        private final Resourceloader plugin;

        public AutoloadCommand(Resourceloader plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
                return true;
            }

            if (!player.hasPermission("resourceloader.autoload")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to set autoload preferences.");
                return true;
            }

            if (args.length < 1) {
                String currentPack = plugin.getConfig().getString("player-packs." + player.getUniqueId());
                if (currentPack != null) {
                    player.sendMessage(ChatColor.YELLOW + "Your current autoload pack is: " + currentPack);
                    player.sendMessage(ChatColor.YELLOW + "Use '/autoload clear' to disable autoload.");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "You don't have an autoload pack set.");
                    player.sendMessage(ChatColor.YELLOW + "Use '/autoload <packname>' to set one.");
                }
                return true;
            }

            String packName = args[0].toLowerCase();

            if (packName.equals("clear")) {
                plugin.getConfig().set("player-packs." + player.getUniqueId(), null);
                plugin.saveConfig();
                player.sendMessage(ChatColor.GREEN + "Cleared your autoload preference.");
                return true;
            }

            if (packName.equals("server")) {
                String serverPack = plugin.getConfig().getString("server-pack");
                if (serverPack == null || serverPack.isEmpty()) {
                    player.sendMessage(ChatColor.RED + "No default server pack configured!");
                    return true;
                }
                plugin.getConfig().set("player-packs." + player.getUniqueId(), "server");
                plugin.saveConfig();
                player.sendMessage(ChatColor.GREEN + "Set your autoload to the default server pack.");
                return true;
            }

            ConfigurationSection packs = plugin.getConfig().getConfigurationSection("resource-packs");
            if (packs == null || !packs.contains(packName)) {
                player.sendMessage(ChatColor.RED + "Resource pack '" + packName + "' not found!");
                return true;
            }

            plugin.getConfig().set("player-packs." + player.getUniqueId(), packName);
            plugin.saveConfig();
            player.sendMessage(ChatColor.GREEN + "Set '" + packName + "' as your autoload resource pack.");

            String resourcePackPath = packs.getString(packName);
            plugin.loadResourcePack(player, packName, resourcePackPath);

            return true;
        }
    }

    public class ListPacksCommand implements CommandExecutor {
        private final Resourceloader plugin;

        public ListPacksCommand(Resourceloader plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("resourceloader.list")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to list resource packs.");
                return true;
            }

            sender.sendMessage(ChatColor.YELLOW + "Available Resource Packs:");

            String serverPack = plugin.getConfig().getString("server-pack");
            if (serverPack != null && !serverPack.isEmpty()) {
                String packType = serverPack.startsWith("http") ? "URL" : "File";
                sender.sendMessage(ChatColor.GREEN + "Default Server Pack: " + ChatColor.GRAY +
                        "(/load) [" + packType + "]");
            }

            ConfigurationSection packs = plugin.getConfig().getConfigurationSection("resource-packs");
            if (packs != null && !packs.getKeys(false).isEmpty()) {
                for (String packName : packs.getKeys(false)) {
                    String packPath = packs.getString(packName);
                    String packType = packPath != null && packPath.startsWith("http") ? "URL" : "File";
                    sender.sendMessage(ChatColor.GRAY + "- " + packName + " (/load " + packName + ") [" + packType + "]");
                }
            }

            return true;
        }
    }

    public class ResourceReloadCommand implements CommandExecutor {
        private final Resourceloader plugin;

        public ResourceReloadCommand(Resourceloader plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("resourceloader.reload")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to reload the Resourceloader plugin.");
                return true;
            }

            try {
                plugin.reloadConfig();
                sender.sendMessage(ChatColor.GREEN + "Resourceloader configuration reloaded successfully!");
                plugin.getLogger().info("Plugin configuration reloaded by " + sender.getName());
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Failed to reload configuration: " + e.getMessage());
                plugin.getLogger().warning("Configuration reload failed: " + e.getMessage());
            }

            return true;
        }
    }
}