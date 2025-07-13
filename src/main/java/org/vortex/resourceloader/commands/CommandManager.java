package org.vortex.resourceloader.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.vortex.resourceloader.Resourceloader;


public class CommandManager {
    private final Resourceloader plugin;
    private final MergeCommand mergeCommand;

    public CommandManager(Resourceloader plugin) {
        this.plugin = plugin;
        this.mergeCommand = new MergeCommand(plugin);
        registerCommands();
    }

    private void registerCommands() {
        // Register load command
        LoadCommand loadCmd = new LoadCommand(plugin);
        plugin.getCommand("load").setExecutor(loadCmd);
        plugin.getCommand("load").setTabCompleter(loadCmd);

        // Register list command
        plugin.getCommand("packlist").setExecutor(new ListPacksCommand(plugin));

        // Register merge commands
        plugin.getCommand("mergepack").setExecutor(mergeCommand);
        plugin.getCommand("mergepack").setTabCompleter(mergeCommand);
        plugin.getCommand("mergegui").setExecutor(new MergeGUICommand(plugin));

        // Register admin commands
        plugin.getCommand("removepack").setExecutor(new RemovePackCommand(plugin));
        plugin.getCommand("removepack").setTabCompleter(new RemovePackCommand(plugin));
        
        CheckPackCommand checkPackCmd = new CheckPackCommand(plugin);
        plugin.getCommand("checkpack").setExecutor(checkPackCmd);
        plugin.getCommand("checkpack").setTabCompleter(checkPackCmd);
        
        plugin.getCommand("resourcereload").setExecutor(new ReloadCommand(plugin));
        plugin.getCommand("clearcache").setExecutor(new ClearCacheCommand(plugin));
        plugin.getCommand("resourceversion").setExecutor(new VersionCommand(plugin));

        // Register autoload command
        AutoLoadCommand autoLoadCmd = new AutoLoadCommand(plugin);
        plugin.getCommand("autoload").setExecutor(autoLoadCmd);
        plugin.getCommand("autoload").setTabCompleter(autoLoadCmd);

        // Register help command
        plugin.getCommand("resourcehelp").setExecutor(new HelpCommand(plugin));
    }

    public MergeCommand getMergeCommand() {
        return mergeCommand;
    }

    private static class HelpCommand implements CommandExecutor {

        public HelpCommand(Resourceloader plugin) {
            // Constructor for consistency
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            sender.sendMessage("§6=== ResourceLoader Help ===");
            
            if (sender.hasPermission("resourceloader.load")) {
                sender.sendMessage("§e/load [pack] §7- Load a resource pack");
                sender.sendMessage("§e/load <pack> §7- Load a specific resource pack");
            }
            
            if (sender.hasPermission("resourceloader.list")) {
                sender.sendMessage("§e/listpacks §7- List available resource packs");
            }

            if (sender.hasPermission("resourceloader.autoload")) {
                sender.sendMessage("§e/autoload <pack|clear> §7- Set automatic pack loading");
            }

            if (sender.hasPermission("resourceloader.admin")) {
                sender.sendMessage("§6=== Admin Commands ===");
                sender.sendMessage("§e/mergepack <output> <pack1> <pack2> §7- Merge resource packs");
                sender.sendMessage("§e/mergegui §7- Open merge GUI");
                sender.sendMessage("§e/removepack <pack> §7- Remove a resource pack");
                sender.sendMessage("§e/checkpack <pack> §7- Validate a resource pack");
                sender.sendMessage("§e/resourcereload §7- Reload configuration");
                sender.sendMessage("§e/resourceversion §7- Check plugin version");
                sender.sendMessage("§e/clearcache §7- Clear resource pack cache");
            }

            sender.sendMessage("§6==========================");
            return true;
        }
    }

    private static class ReloadCommand implements CommandExecutor {
        private final Resourceloader plugin;

        public ReloadCommand(Resourceloader plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("resourceloader.admin")) {
                sender.sendMessage(plugin.getMessageManager().getMessage("general.no-permission"));
                return true;
            }

            try {
                plugin.reloadConfig();
                plugin.getMessageManager().reloadMessages();
                plugin.loadResourcePacks(true);
                sender.sendMessage(plugin.getMessageManager().getMessage("general.reload-success"));
            } catch (Exception e) {
                sender.sendMessage(plugin.getMessageManager().formatMessage("general.reload-failed", 
                    "error", e.getMessage()));
            }

            return true;
        }
    }

    private static class ClearCacheCommand implements CommandExecutor {
        private final Resourceloader plugin;

        public ClearCacheCommand(Resourceloader plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("resourceloader.admin")) {
                sender.sendMessage(plugin.getMessageManager().getMessage("general.no-permission"));
                return true;
            }

            plugin.getPackManager().getPackCache().clearCache();
            sender.sendMessage(plugin.getMessageManager().getMessage("cache.cleared"));
            return true;
        }
    }

    private static class ListPacksCommand implements CommandExecutor {
        private final Resourceloader plugin;

        public ListPacksCommand(Resourceloader plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("resourceloader.list")) {
                sender.sendMessage(plugin.getMessageManager().getMessage("general.no-permission"));
                return true;
            }

            sender.sendMessage(plugin.getMessageManager().getMessage("list.header"));

            if (plugin.getResourcePacks().isEmpty()) {
                sender.sendMessage(plugin.getMessageManager().getMessage("list.no-packs"));
                sender.sendMessage(plugin.getMessageManager().getMessage("list.footer"));
                return true;
            }

            String serverPack = plugin.getConfig().getString("server-pack");
            if (serverPack != null && !serverPack.isEmpty()) {
                String packType = serverPack.startsWith("http") ? "URL" : "File";
                sender.sendMessage(plugin.getMessageManager().formatMessage("list.default-pack", 
                    "type", packType));
            }

            plugin.getResourcePacks().forEach((name, file) -> {
                if (!name.equals("server")) {
                    String packType = file == null ? "URL" : "File";
                    sender.sendMessage(plugin.getMessageManager().formatMessage("list.pack-entry",
                        "pack", name, "type", packType));
                }
            });

            sender.sendMessage(plugin.getMessageManager().getMessage("list.footer"));
            return true;
        }
    }

    private static class VersionCommand implements CommandExecutor {
        private final Resourceloader plugin;

        public VersionCommand(Resourceloader plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("resourceloader.admin")) {
                sender.sendMessage(plugin.getMessageManager().getMessage("general.no-permission"));
                return true;
            }

            String currentVersion = plugin.getDescription().getVersion();
            sender.sendMessage("§eChecking for updates...");

            // Use async task to check for updates
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    java.net.URL url = java.net.URI.create("https://api.github.com/repos/DefectiveVortex/Resourceloader/releases/latest").toURL();
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);

                    if (conn.getResponseCode() == java.net.HttpURLConnection.HTTP_OK) {
                        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;

                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();

                        // Parse JSON response
                        String jsonResponse = response.toString();
                        String latestVersion = null;
                        
                        // Simple JSON parsing to extract tag_name
                        int tagStart = jsonResponse.indexOf("\"tag_name\":\"") + 12;
                        if (tagStart > 11) {
                            int tagEnd = jsonResponse.indexOf("\"", tagStart);
                            if (tagEnd > tagStart) {
                                latestVersion = jsonResponse.substring(tagStart, tagEnd).replace("v", "");
                            }
                        }

                        final String finalLatestVersion = latestVersion;
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (finalLatestVersion != null) {
                                sender.sendMessage("§eCurrent version: §f" + currentVersion);
                                sender.sendMessage("§eLatest version: §f" + finalLatestVersion);

                                if (!currentVersion.equals(finalLatestVersion)) {
                                    sender.sendMessage("§aA new version is available!");
                                    sender.sendMessage("§eDownload it from: §fhttps://github.com/DefectiveVortex/Resourceloader/releases/latest");
                                } else {
                                    sender.sendMessage("§aYou are running the latest version!");
                                }
                            } else {
                                sender.sendMessage("§cFailed to parse version information.");
                            }
                        });
                    } else {
                        final int responseCode = conn.getResponseCode();
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            sender.sendMessage("§cFailed to check for updates. HTTP " + responseCode);
                        });
                    }
                } catch (Exception e) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        sender.sendMessage("§cFailed to check for updates: " + e.getMessage());
                        sender.sendMessage("§eCurrent version: §f" + currentVersion);
                    });
                }
            });

            return true;
        }
    }
} 