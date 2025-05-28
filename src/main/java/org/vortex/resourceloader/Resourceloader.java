// ResourceLoader.java
package org.vortex.resourceloader;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.TabCompleter;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.util.StringUtil;
import org.vortex.resourceloader.util.FileUtil;
import java.util.Collections;
import org.vortex.resourceloader.core.ResourcePackManager;
import org.vortex.resourceloader.compression.PackCompressor;
import org.vortex.resourceloader.gui.MergeGUI;
import org.vortex.resourceloader.util.MessageManager;
import org.vortex.resourceloader.commands.CommandManager;
import org.vortex.resourceloader.listeners.ResourcePackEnforcer;
import org.vortex.resourceloader.listeners.EarlyPackLoader;

public final class Resourceloader extends JavaPlugin {
    private final ResourcePackManager packManager;
    private final PackCompressor packCompressor;
    private MergeGUI mergeGUI;
    private final MessageManager messageManager;

    public Resourceloader() {
        this.messageManager = new MessageManager(this);
        this.packManager = new ResourcePackManager(this);
        this.packCompressor = new PackCompressor(this);
    }

    @Override
    public void onEnable() {
        // Create plugin directory if it doesn't exist
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Save default config if it doesn't exist
        saveDefaultConfig();
        
        // Register commands
        new CommandManager(this);

        // Initialize GUI if enabled
        if (getConfig().getBoolean("gui.enabled", true)) {
            mergeGUI = new MergeGUI(this);
        }

        // Initialize early pack loading with server.properties if enabled
        if (getConfig().getBoolean("enforcement.use-server-properties", false)) {
            new EarlyPackLoader(this);
            getLogger().info("Early resource pack loading enabled with server.properties");
        } else {
            // Register regular resource pack enforcer
            getServer().getPluginManager().registerEvents(new ResourcePackEnforcer(this), this);
        }

        getLogger().info("ResourceLoader has been enabled!");
    }

    @Override
    public void onDisable() {
        if (packManager != null) {
            packManager.shutdown();
        }
        if (packCompressor != null) {
            packCompressor.shutdown();
        }
        getLogger().info("ResourceLoader has been disabled!");
    }

    public void loadResourcePacks() {
        loadResourcePacks(false);
    }

    public void loadResourcePacks(boolean silent) {
        packManager.loadResourcePacks(silent);
    }

    public Map<String, File> getResourcePacks() {
        return packManager.getResourcePacks();
    }

    public ResourcePackManager getPackManager() {
        return packManager;
    }

    public PackCompressor getPackCompressor() {
        return packCompressor;
    }

    public MergeGUI getMergeGUI() {
        return mergeGUI;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public class LoadCommand implements CommandExecutor, TabCompleter {
        private final Resourceloader plugin;

        public LoadCommand(Resourceloader plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.getMessageManager().getMessage("general.player-only"));
                return true;
            }

            if (!player.hasPermission("resourceloader.load")) {
                player.sendMessage(plugin.getMessageManager().getMessage("general.no-permission"));
                return true;
            }

            if (args.length < 1) {
                String defaultPackPath = plugin.getConfig().getString("server-pack");
                if (defaultPackPath == null || defaultPackPath.isEmpty()) {
                    player.sendMessage(plugin.getMessageManager().getMessage("resource-packs.no-default"));
                    return true;
                }

                loadResourcePack(player, "server", defaultPackPath);
                return true;
            }

            String packName = args[0].toLowerCase();
            ConfigurationSection packs = plugin.getConfig().getConfigurationSection("resource-packs");

            if (!plugin.getResourcePacks().containsKey(packName)) {
                player.sendMessage(plugin.getMessageManager().formatMessage("resource-packs.not-found", 
                    "pack", packName));
                return true;
            }

            String packPath = packs.getString(packName);
            loadResourcePack(player, packName, packPath);
            return true;
        }

        public void loadResourcePack(Player player, String packName, String packPath) {
            if (packPath == null || packPath.isEmpty()) {
                player.sendMessage(plugin.getMessageManager().formatMessage("resource-packs.invalid-path", 
                    "pack", packName));
                return;
            }

            try {
                if (packPath.startsWith("http://") || packPath.startsWith("https://")) {
                    player.sendMessage(plugin.getMessageManager().formatMessage("resource-packs.loading", 
                        "pack", packName));
                    
                    plugin.getPackManager().getPackCache().getCachedPack(packPath, packName)
                        .thenAccept(cachedFile -> {
                            try {
                                byte[] fileHash = FileUtil.calcSHA1(cachedFile);
                                String downloadUrl = plugin.getPackManager().getPackServer().createDownloadURL(player, packName, cachedFile.getName());
                                player.setResourcePack(downloadUrl, fileHash);
                                player.sendMessage(plugin.getMessageManager().getMessage("resource-packs.load-success"));
                            } catch (Exception e) {
                                player.sendMessage(plugin.getMessageManager().formatMessage("resource-packs.load-failed", 
                                    "error", e.getMessage()));
                                plugin.getLogger().warning("Failed to load cached pack: " + e.getMessage());
                            }
                        })
                        .exceptionally(e -> {
                            player.sendMessage(plugin.getMessageManager().formatMessage("resource-packs.load-failed", 
                                "error", e.getMessage()));
                            plugin.getLogger().warning("Failed to load pack: " + e.getMessage());
                            return null;
                        });
                } else {
                    File packFile = plugin.getResourcePacks().get(packName);
                    if (packFile == null || !packFile.exists()) {
                        player.sendMessage(plugin.getMessageManager().getMessage("resource-packs.file-not-found"));
                        return;
                    }
                    String finalUrl = plugin.getPackManager().getPackServer().createDownloadURL(player, packName, packPath);
                    byte[] hash = FileUtil.calcSHA1(packFile);

                if (hash != null) {
                    player.setResourcePack(finalUrl, hash);
                } else {
                    player.setResourcePack(finalUrl);
                }

                    player.sendMessage(plugin.getMessageManager().formatMessage("resource-packs.loading", 
                        "pack", packName));
                }

                plugin.getLogger().info("Resource pack '" + packName + "' load attempted for player " + player.getName());
            } catch (Exception e) {
                player.sendMessage(plugin.getMessageManager().formatMessage("resource-packs.load-failed", 
                    "error", e.getMessage()));
                plugin.getLogger().warning("Resource pack loading failed for " + packName + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            List<String> completions = new ArrayList<>();

            if (args.length == 1) {
                if (sender.hasPermission("resourceloader.load")) {
                    List<String> packs = new ArrayList<>(plugin.getResourcePacks().keySet());
                    StringUtil.copyPartialMatches(args[0], packs, completions);
                }
            }

            Collections.sort(completions);
            return completions;
        }
    }
}