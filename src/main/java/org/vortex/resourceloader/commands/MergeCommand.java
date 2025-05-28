package org.vortex.resourceloader.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;
import org.vortex.resourceloader.Resourceloader;
import org.vortex.resourceloader.ResourcePackMerger;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MergeCommand implements CommandExecutor, TabCompleter {
    private final Resourceloader plugin;
    private final AtomicBoolean isMerging;

    public MergeCommand(Resourceloader plugin) {
        this.plugin = plugin;
        this.isMerging = new AtomicBoolean(false);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("resourceloader.admin")) {
            sender.sendMessage(plugin.getMessageManager().getMessage("general.no-permission"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.getMessageManager().getMessage("merge.invalid-usage"));
            return true;
        }

        if (isMerging.get()) {
            sender.sendMessage(plugin.getMessageManager().getMessage("merge.already-merging"));
            return true;
        }

        final String outputName = args[0].toLowerCase().endsWith(".zip") ? args[0] : args[0] + ".zip";

        // Check if output pack already exists
        File outputFile = new File(plugin.getDataFolder(), "packs/" + outputName);
        if (outputFile.exists()) {
            sender.sendMessage(plugin.getMessageManager().formatMessage("merge.output-exists", 
                "pack", outputName));
            return true;
        }

        List<File> packsToMerge = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String packName = args[i];
            File packFile = plugin.getResourcePacks().get(packName);
            if (packFile == null || !packFile.exists()) {
                sender.sendMessage(plugin.getMessageManager().formatMessage("merge.invalid-pack", 
                    "pack", packName));
                return true;
            }
            packsToMerge.add(packFile);
        }

        if (packsToMerge.size() < 2) {
            sender.sendMessage(plugin.getMessageManager().getMessage("merge.no-packs"));
            return true;
        }

        isMerging.set(true);
        sender.sendMessage(plugin.getMessageManager().getMessage("merge.started"));

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ResourcePackMerger merger = new ResourcePackMerger(plugin);
                File result = merger.mergeResourcePacks(packsToMerge, outputName);
                
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (result != null && result.exists()) {
                        sender.sendMessage(plugin.getMessageManager().formatMessage("merge.success", 
                            "pack", outputName));
                        plugin.loadResourcePacks(true);
                    } else {
                        sender.sendMessage(plugin.getMessageManager().getMessage("merge.failed"));
                    }
                    isMerging.set(false);
                });
            } catch (Exception e) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(plugin.getMessageManager().formatMessage("merge.failed", 
                        "error", e.getMessage()));
                    isMerging.set(false);
                });
            }
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (!sender.hasPermission("resourceloader.admin")) {
            return completions;
        }

        if (args.length >= 2) {
            List<String> packs = new ArrayList<>(plugin.getResourcePacks().keySet());
            StringUtil.copyPartialMatches(args[args.length - 1], packs, completions);
        }

        Collections.sort(completions);
        return completions;
    }
} 