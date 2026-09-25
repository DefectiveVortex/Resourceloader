package org.vortex.resourceloader.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.vortex.resourceloader.Resourceloader;
import org.vortex.resourceloader.util.ConfigFileUpdater;
import org.vortex.resourceloader.util.ConfigRepair;
import org.vortex.resourceloader.util.MessageManager;


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
        private final Resourceloader plugin;

        public HelpCommand(Resourceloader plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            MessageManager messages = plugin.getMessageManager();
            sender.sendMessage(messages.getMessageNoPrefix("help.header"));

            if (sender.hasPermission("resourceloader.load")) {
                sender.sendMessage(messages.getMessageNoPrefix("help.load"));
                sender.sendMessage(messages.getMessageNoPrefix("help.load-specific"));
            }

            if (sender.hasPermission("resourceloader.list")) {
                sender.sendMessage(messages.getMessageNoPrefix("help.list"));
            }

            if (sender.hasPermission("resourceloader.autoload")) {
                sender.sendMessage(messages.getMessageNoPrefix("help.autoload"));
            }

            if (sender.hasPermission("resourceloader.admin")) {
                sender.sendMessage(messages.getMessageNoPrefix("help.admin-header"));
                sender.sendMessage(messages.getMessageNoPrefix("help.merge"));
                sender.sendMessage(messages.getMessageNoPrefix("help.mergegui"));
                sender.sendMessage(messages.getMessageNoPrefix("help.remove"));
                sender.sendMessage(messages.getMessageNoPrefix("help.checkpack"));
                sender.sendMessage(messages.getMessageNoPrefix("help.reload"));
                sender.sendMessage(messages.getMessageNoPrefix("help.version"));
                sender.sendMessage(messages.getMessageNoPrefix("help.cache"));
            }

            sender.sendMessage(messages.getMessageNoPrefix("help.footer"));
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
                ConfigFileUpdater.updateBundledConfigs(plugin);
                ConfigRepair.repair(plugin);
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
                String packType = plugin.getMessageManager().getMessageNoPrefix(
                    serverPack.startsWith("http") ? "list.type-url" : "list.type-file");
                sender.sendMessage(plugin.getMessageManager().formatMessage("list.default-pack",
                    "type", packType));
            }

            plugin.getResourcePacks().forEach((name, file) -> {
                if (!name.equals("server")) {
                    String packType = plugin.getMessageManager().getMessageNoPrefix(
                        file == null ? "list.type-url" : "list.type-file");
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

            MessageManager messages = plugin.getMessageManager();
            String currentVersion = plugin.getDescription().getVersion();
            sender.sendMessage(messages.getMessage("updates.checking"));

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
                                sender.sendMessage(messages.formatMessageNoPrefix("updates.current-version",
                                    "version", currentVersion));
                                sender.sendMessage(messages.formatMessageNoPrefix("updates.latest-version",
                                    "version", finalLatestVersion));

                                if (!currentVersion.equals(finalLatestVersion)) {
                                    sender.sendMessage(messages.getMessageNoPrefix("updates.new-version"));
                                    sender.sendMessage(messages.formatMessageNoPrefix("updates.download",
                                        "url", "https://github.com/DefectiveVortex/Resourceloader/releases/latest"));
                                } else {
                                    sender.sendMessage(messages.getMessageNoPrefix("updates.up-to-date"));
                                }
                            } else {
                                sender.sendMessage(messages.getMessage("updates.parse-failed"));
                            }
                        });
                    } else {
                        final int responseCode = conn.getResponseCode();
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            sender.sendMessage(messages.formatMessage("updates.http-error",
                                "code", responseCode));
                        });
                    }
                } catch (Exception e) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(messages.formatMessage("updates.check-error",
                            "error", String.valueOf(e.getMessage())));
                        sender.sendMessage(messages.formatMessageNoPrefix("updates.current-version",
                            "version", currentVersion));
                    });
                }
            });

            return true;
        }
    }
}
