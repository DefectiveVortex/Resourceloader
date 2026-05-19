package org.vortex.resourceloader;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.Map;
import org.vortex.resourceloader.core.ResourcePackManager;
import org.vortex.resourceloader.compression.PackCompressor;
import org.vortex.resourceloader.gui.MergeGUI;
import org.vortex.resourceloader.util.ConfigFileUpdater;
import org.vortex.resourceloader.util.MessageManager;
import org.vortex.resourceloader.commands.CommandManager;
import org.vortex.resourceloader.listeners.ResourcePackEnforcer;
import org.vortex.resourceloader.listeners.EarlyPackLoader;
import org.vortex.resourceloader.listeners.ResourcePackStatusListener;

public final class Resourceloader extends JavaPlugin {
    private ResourcePackManager packManager;
    private PackCompressor packCompressor;
    private MergeGUI mergeGUI;
    private MessageManager messageManager;

    @Override
    public void onEnable() {
        // Create plugin directory if it doesn't exist
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Repair bundled config files before any manager reads them
        ConfigFileUpdater.updateBundledConfigs(this);
        reloadConfig();

        messageManager = new MessageManager(this);
        packManager = new ResourcePackManager(this);
        packCompressor = new PackCompressor(this);

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
