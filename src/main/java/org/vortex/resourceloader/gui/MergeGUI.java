package org.vortex.resourceloader.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.vortex.resourceloader.Resourceloader;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MergeGUI implements Listener {
    private final Resourceloader plugin;
    private final Map<UUID, List<String>> selectedPacks;
    private final Map<UUID, String> outputNames;
    private final Set<UUID> openInventories;
    private static final int MAX_PACKS = 45; // Maximum number of packs that can be displayed
    private static final Sound SELECT_SOUND = Sound.BLOCK_NOTE_BLOCK_PLING;
    private static final Sound ERROR_SOUND = Sound.BLOCK_NOTE_BLOCK_BASS;
    private static final Sound SUCCESS_SOUND = Sound.ENTITY_PLAYER_LEVELUP;

    public MergeGUI(Resourceloader plugin) {
        this.plugin = plugin;
        this.selectedPacks = new ConcurrentHashMap<>();
        this.outputNames = new ConcurrentHashMap<>();
        this.openInventories = ConcurrentHashMap.newKeySet();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openMergeGUI(Player player, String outputName) {
        // Validate output name
        if (!outputName.toLowerCase().endsWith(".zip")) {
            outputName += ".zip";
        }

        // Get available packs and sort them
        List<Map.Entry<String, File>> availablePacks = plugin.getResourcePacks().entrySet().stream()
            .filter(entry -> entry.getValue() != null)
            .sorted(Map.Entry.comparingByKey())
            .collect(Collectors.toList());

        // Check if there are too many packs
        if (availablePacks.size() > MAX_PACKS) {
            player.sendMessage(ChatColor.RED + "Too many resource packs to display in GUI. Maximum: " + MAX_PACKS);
            return;
        }

        // Create inventory with appropriate size
        int size = Math.min(54, ((availablePacks.size() / 9) + 1) * 9 + 9);
        Inventory inv = Bukkit.createInventory(null, size, ChatColor.DARK_PURPLE + "Resource Pack Merger");

        // Store player data
        outputNames.put(player.getUniqueId(), outputName);
        selectedPacks.put(player.getUniqueId(), new ArrayList<>());
        openInventories.add(player.getUniqueId());

        try {
            // Add available packs
            int slot = 0;
            for (Map.Entry<String, File> entry : availablePacks) {
                ItemStack item = createPackItem(entry.getKey(), entry.getValue());
                inv.setItem(slot++, item);
            }

            // Add control buttons at the bottom row
            int bottomRow = size - 9;
            inv.setItem(bottomRow + 0, createControlItem(Material.LIME_WOOL, "Merge Selected Packs", 
                Arrays.asList("Click to merge the selected packs", "Output: " + outputName)));
            inv.setItem(bottomRow + 1, createControlItem(Material.YELLOW_WOOL, "Preview Merge", 
                Collections.singletonList("Click to preview the merged pack")));
            inv.setItem(bottomRow + 8, createControlItem(Material.RED_WOOL, "Cancel", 
                Collections.singletonList("Click to cancel merging")));

            player.openInventory(inv);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create merge GUI: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "Failed to open merge GUI. Please try again.");
            cleanup(player.getUniqueId());
        }
    }

    private ItemStack createPackItem(String name, File file) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + name);
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "File: " + file.getName());
            lore.add(ChatColor.GRAY + "Size: " + formatFileSize(file.length()));
            lore.add("");
            lore.add(ChatColor.YELLOW + "Click to select/deselect");
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private ItemStack createControlItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + name);
            
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(ChatColor.GRAY + line);
            }
            meta.setLore(coloredLore);
            
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!openInventories.contains(player.getUniqueId())) return;
        if (!event.getView().getTitle().equals(ChatColor.DARK_PURPLE + "Resource Pack Merger")) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta() || clicked.getItemMeta() == null) return;

        List<String> selectedList = selectedPacks.get(player.getUniqueId());
        String outputName = outputNames.get(player.getUniqueId());

        if (clicked.getType() == Material.BOOK || clicked.getType() == Material.ENCHANTED_BOOK) {
            handlePackSelection(player, event, clicked, selectedList);
        } else if (clicked.getType() == Material.LIME_WOOL) {
            handleMergeAction(player, selectedList, outputName);
        } else if (clicked.getType() == Material.YELLOW_WOOL) {
            handlePreviewAction(player, selectedList);
        } else if (clicked.getType() == Material.RED_WOOL) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "Merge operation cancelled.");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }

    private void handlePackSelection(Player player, InventoryClickEvent event, ItemStack clicked, List<String> selectedList) {
        String packName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        if (selectedList.contains(packName)) {
            selectedList.remove(packName);
            clicked.setType(Material.BOOK);
            player.playSound(player.getLocation(), SELECT_SOUND, 1.0f, 0.8f);
        } else {
            selectedList.add(packName);
            clicked.setType(Material.ENCHANTED_BOOK);
            player.playSound(player.getLocation(), SELECT_SOUND, 1.0f, 1.2f);
        }
        event.getInventory().setItem(event.getSlot(), clicked);
        
        // Update item lore with selection status
        ItemMeta meta = clicked.getItemMeta();
        List<String> lore = meta.getLore();
        lore.set(lore.size() - 1, ChatColor.YELLOW + (selectedList.contains(packName) ? "✓ Selected" : "Click to select/deselect"));
        meta.setLore(lore);
        clicked.setItemMeta(meta);
    }

    private void handleMergeAction(Player player, List<String> selectedList, String outputName) {
        if (selectedList.size() < 2) {
            player.sendMessage(ChatColor.RED + "Please select at least 2 packs to merge!");
            player.playSound(player.getLocation(), ERROR_SOUND, 1.0f, 0.8f);
            return;
        }
        player.closeInventory();
        player.playSound(player.getLocation(), SUCCESS_SOUND, 1.0f, 1.0f);
        executeMerge(player, selectedList, outputName);
    }

    private void handlePreviewAction(Player player, List<String> selectedList) {
        if (selectedList.size() < 2) {
            player.sendMessage(ChatColor.RED + "Please select at least 2 packs to preview!");
            player.playSound(player.getLocation(), ERROR_SOUND, 1.0f, 0.8f);
            return;
        }
        player.playSound(player.getLocation(), SELECT_SOUND, 1.0f, 1.0f);
        previewMerge(player, selectedList);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        cleanup(player.getUniqueId());
    }

    private void cleanup(UUID playerId) {
        openInventories.remove(playerId);
        selectedPacks.remove(playerId);
        outputNames.remove(playerId);
    }

    private void executeMerge(Player player, List<String> packs, String outputName) {
        try {
            List<File> packFiles = new ArrayList<>();
            for (String packName : packs) {
                File packFile = plugin.getResourcePacks().get(packName);
                if (packFile != null && packFile.exists()) {
                    packFiles.add(packFile);
                }
            }

            if (packFiles.size() < 2) {
                player.sendMessage(ChatColor.RED + "Error: Not enough valid packs selected!");
                player.playSound(player.getLocation(), ERROR_SOUND, 1.0f, 0.8f);
                return;
            }

            player.sendMessage(ChatColor.YELLOW + "Merging " + packFiles.size() + " resource packs...");
            plugin.getServer().dispatchCommand(player, "mergepack " + outputName + " " + 
                String.join(" ", packs));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to execute merge: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "Failed to merge packs. Please try again.");
            player.playSound(player.getLocation(), ERROR_SOUND, 1.0f, 0.8f);
        }
    }

    private void previewMerge(Player player, List<String> packs) {
        try {
            player.sendMessage(ChatColor.YELLOW + "Selected packs to merge:");
            long totalSize = 0;
            for (String pack : packs) {
                File packFile = plugin.getResourcePacks().get(pack);
                if (packFile != null && packFile.exists()) {
                    long size = packFile.length();
                    totalSize += size;
                    player.sendMessage(ChatColor.GRAY + "- " + pack + " (" + formatFileSize(size) + ")");
                }
            }
            player.sendMessage(ChatColor.YELLOW + "Total size: " + formatFileSize(totalSize));
            player.sendMessage(ChatColor.YELLOW + "Use the merge button to combine these packs.");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to preview merge: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "Failed to generate preview. Please try again.");
            player.playSound(player.getLocation(), ERROR_SOUND, 1.0f, 0.8f);
        }
    }
} 