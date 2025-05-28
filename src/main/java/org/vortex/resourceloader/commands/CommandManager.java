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
            sender.sendMessage(plugin.getMessageManager().getMessage("help.header"));
            
            if (sender.hasPermission("resourceloader.load")) {
                sender.sendMessage(plugin.getMessageManager().getMessageNoPrefix("help.load"));
                sender.sendMessage(plugin.getMessageManager().getMessageNoPrefix("help.load-specific"));
            }
            
            if (sender.hasPermission("resourceloader.list")) {
                sender.sendMessage(plugin.getMessageManager().getMessageNoPrefix("help.list"));
            }

            if (sender.hasPermission("resourceloader.autoload")) {
                sender.sendMessage(plugin.getMessageManager().getMessageNoPrefix("help.autoload"));
            }

            if (sender.hasPermission("resourceloader.admin")) {
                sender.sendMessage(plugin.getMessageManager().getMessageNoPrefix("help.admin-header"));
                sender.sendMessage(plugin.getMessageManager().getMessageNoPrefix("help.merge"));
                sender.sendMessage(plugin.getMessageManager().getMessageNoPrefix("help.remove"));
                sender.sendMessage(plugin.getMessageManager().getMessageNoPrefix("help.reload"));
                sender.sendMessage(plugin.getMessageManager().getMessageNoPrefix("help.version"));
                sender.sendMessage(plugin.getMessageManager().getMessageNoPrefix("help.cache"));
            }

            sender.sendMessage(plugin.getMessageManager().getMessageNoPrefix("help.footer"));
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

            String version = plugin.getDescription().getVersion();
            sender.sendMessage(plugin.getMessageManager().formatMessage("version.current", 
                "version", version));
            return true;
        }
    }
} 