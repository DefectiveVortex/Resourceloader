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
import org.vortex.resourceloader.listeners.ResourcePackStatusListener;

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

        // Register resource pack status listener
        getServer().getPluginManager().registerEvents(new ResourcePackStatusListener(this), this);

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
}