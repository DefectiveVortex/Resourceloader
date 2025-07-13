package org.vortex.resourceloader.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.vortex.resourceloader.Resourceloader;
import org.vortex.resourceloader.util.MessageManager;
import org.vortex.resourceloader.validation.PackValidator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CheckPackCommand implements CommandExecutor, TabCompleter {
    private final Resourceloader plugin;
    private final MessageManager messageManager;
    private final PackValidator validator;

    public CheckPackCommand(Resourceloader plugin) {
        this.plugin = plugin;
        this.messageManager = plugin.getMessageManager();
        this.validator = new PackValidator(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("resourceloader.admin")) {
            sender.sendMessage(messageManager.getMessage("general.no-permission"));
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("§cUsage: /checkpack <packname>");
            return true;
        }

        String packName = args[0];
        
        // Check if pack exists in configuration
        if (!plugin.getConfig().contains("resource-packs." + packName)) {
            sender.sendMessage("§cResource pack '" + packName + "' not found in configuration.");
            return true;
        }

        String packPath = plugin.getConfig().getString("resource-packs." + packName);
        File packFile;

        // Determine if it's a local file or URL
        if (packPath.startsWith("http://") || packPath.startsWith("https://")) {
            sender.sendMessage("§eValidating URL-based pack: " + packName);
            sender.sendMessage("§7Note: URL packs are validated when downloaded. Use this command on local files for detailed validation.");
            return true;
        } else {
            // Local file
            File packsDir = new File(plugin.getDataFolder(), "packs");
            packFile = new File(packsDir, packPath);
            
            if (!packFile.exists()) {
                sender.sendMessage("§cPack file not found: " + packFile.getPath());
                return true;
            }
        }

        sender.sendMessage("§eValidating resource pack: §f" + packName);
        sender.sendMessage("§7File: " + packFile.getName());
        
        // Perform validation
        PackValidator.ValidationResult result = validator.validate(packFile);
        
        // Display results
        if (result.isValid()) {
            sender.sendMessage("§a✓ Pack validation successful!");
        } else {
            sender.sendMessage("§c✗ Pack validation failed!");
        }
        
        // Show formatted results
        List<String> formattedResults = result.getFormattedIssues();
        for (String line : formattedResults) {
            if (line.startsWith("Critical Issues:")) {
                sender.sendMessage("§c" + line);
            } else if (line.startsWith("Warnings:")) {
                sender.sendMessage("§e" + line);
            } else if (line.startsWith("- ")) {
                // Individual issue
                if (formattedResults.indexOf(line) > 0 && 
                    formattedResults.get(formattedResults.indexOf(line) - 1).startsWith("Critical Issues:")) {
                    sender.sendMessage("§c" + line);
                } else if (formattedResults.indexOf(line) > 0 && 
                           formattedResults.get(formattedResults.indexOf(line) - 1).startsWith("Warnings:")) {
                    sender.sendMessage("§e" + line);
                } else {
                    sender.sendMessage("§7" + line);
                }
            } else {
                sender.sendMessage("§7" + line);
            }
        }
        
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // Return available pack names
            if (plugin.getConfig().contains("resource-packs")) {
                for (String packName : plugin.getConfig().getConfigurationSection("resource-packs").getKeys(false)) {
                    if (packName.toLowerCase().startsWith(args[0].toLowerCase())) {
                        completions.add(packName);
                    }
                }
            }
        }
        
        return completions;
    }
}
