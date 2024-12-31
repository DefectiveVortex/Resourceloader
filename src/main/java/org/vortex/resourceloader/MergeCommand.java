package org.vortex.resourceloader;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MergeCommand implements CommandExecutor, TabCompleter {
    private final Resourceloader plugin;
    private final ResourcePackMerger merger;
    private boolean isCurrentlyMerging = false;

    public MergeCommand(Resourceloader plugin) {
        this.plugin = plugin;
        this.merger = new ResourcePackMerger(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("resourceloader.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to merge resource packs.");
            return true;
        }

        if (isCurrentlyMerging) {
            sender.sendMessage(ChatColor.RED + "A merge operation is already in progress. Please wait.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /mergepack <output_name> <pack1> <pack2> [pack3...]");
            return true;
        }

        String outputName = args[0];
        if (!outputName.toLowerCase().endsWith(".zip")) {
            outputName += ".zip";
        }

        // Validate all packs exist before starting the merge
        List<File> inputPacks = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String packName = args[i];
            File packFile = plugin.getResourcePacks().get(packName);
            if (packFile == null) {
                sender.sendMessage(ChatColor.RED + "Resource pack '" + packName + "' not found!");
                return true;
            }
            inputPacks.add(packFile);
        }

        // Mark as merging
        isCurrentlyMerging = true;
        sender.sendMessage(ChatColor.YELLOW + "Starting resource pack merge process...");

        // Use final variables for the inner class
        final String finalOutputName = outputName;
        final List<File> finalInputPacks = inputPacks;

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // Perform merge operation async
                    File outputFile = merger.mergeResourcePacks(finalInputPacks, finalOutputName);

                    // Switch back to main thread to update plugin state
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            try {
                                plugin.loadResourcePacks();
                                sender.sendMessage(ChatColor.GREEN + "Successfully merged resource packs into: " + finalOutputName);
                            } catch (Exception e) {
                                sender.sendMessage(ChatColor.RED + "Error loading merged pack: " + e.getMessage());
                                plugin.getLogger().warning("Error loading merged pack: " + e.getMessage());
                                e.printStackTrace();
                            } finally {
                                isCurrentlyMerging = false;
                            }
                        }
                    }.runTask(plugin);

                } catch (IOException e) {
                    // Switch back to main thread to send error message
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            sender.sendMessage(ChatColor.RED + "Failed to merge resource packs: " + e.getMessage());
                            plugin.getLogger().warning("Failed to merge resource packs: " + e.getMessage());
                            e.printStackTrace();
                            isCurrentlyMerging = false;
                        }
                    }.runTask(plugin);
                }
            }
        }.runTaskAsynchronously(plugin);

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length >= 2) {
            if (sender.hasPermission("resourceloader.admin")) {
                List<String> packs = new ArrayList<>(plugin.getResourcePacks().keySet());
                StringUtil.copyPartialMatches(args[args.length - 1], packs, completions);
            }
        }

        Collections.sort(completions);
        return completions;
    }
}