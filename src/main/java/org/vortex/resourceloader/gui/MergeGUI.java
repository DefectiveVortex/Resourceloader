package org.vortex.resourceloader.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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

public class MergeGUI implements Listener {
    private final Resourceloader plugin;
    private final Map<UUID, List<String>> selectedPacks;
    private final Map<UUID, String> outputNames;
    private final Set<UUID> openInventories;

    public MergeGUI(Resourceloader plugin) {
        this.plugin = plugin;
        this.selectedPacks = new HashMap<>();
        this.outputNames = new HashMap<>();
        this.openInventories = new HashSet<>();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openMergeGUI(Player player, String outputName) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "Resource Pack Merger");
        outputNames.put(player.getUniqueId(), outputName);
        selectedPacks.put(player.getUniqueId(), new ArrayList<>());
        openInventories.add(player.getUniqueId());

        // Add available packs
        int slot = 0;
        for (Map.Entry<String, File> entry : plugin.getResourcePacks().entrySet()) {
            if (entry.getValue() != null) { // Only show file-based packs
                ItemStack item = createPackItem(entry.getKey(), entry.getValue());
                inv.setItem(slot++, item);
            }
        }

        // Add control buttons
        inv.setItem(45, createControlItem(Material.LIME_WOOL, "Merge Selected Packs", 
            Arrays.asList("Click to merge the selected packs", "Output: " + outputName)));
        inv.setItem(46, createControlItem(Material.YELLOW_WOOL, "Preview Merge", 
            Collections.singletonList("Click to preview the merged pack")));
        inv.setItem(53, createControlItem(Material.RED_WOOL, "Cancel", 
            Collections.singletonList("Click to cancel merging")));

        player.openInventory(inv);
    }

    private ItemStack createPackItem(String name, File file) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + name);
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "File: " + file.getName());
        lore.add(ChatColor.GRAY + "Size: " + (file.length() / 1024) + "KB");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to select/deselect");
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createControlItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + name);
        
        List<String> coloredLore = new ArrayList<>();
        for (String line : lore) {
            coloredLore.add(ChatColor.GRAY + line);
        }
        meta.setLore(coloredLore);
        
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!openInventories.contains(player.getUniqueId())) return;
        if (!event.getView().getTitle().equals(ChatColor.DARK_PURPLE + "Resource Pack Merger")) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        List<String> selectedList = selectedPacks.get(player.getUniqueId());
        String outputName = outputNames.get(player.getUniqueId());

        if (clicked.getType() == Material.BOOK) {
            String packName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
            if (selectedList.contains(packName)) {
                selectedList.remove(packName);
                clicked.setType(Material.BOOK);
            } else {
                selectedList.add(packName);
                clicked.setType(Material.ENCHANTED_BOOK);
            }
            event.getInventory().setItem(event.getSlot(), clicked);
        } else if (clicked.getType() == Material.LIME_WOOL) {
            if (selectedList.size() < 2) {
                player.sendMessage(ChatColor.RED + "Please select at least 2 packs to merge!");
                return;
            }
            player.closeInventory();
            executeMerge(player, selectedList, outputName);
        } else if (clicked.getType() == Material.YELLOW_WOOL) {
            if (selectedList.size() < 2) {
                player.sendMessage(ChatColor.RED + "Please select at least 2 packs to preview!");
                return;
            }
            previewMerge(player, selectedList);
        } else if (clicked.getType() == Material.RED_WOOL) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "Merge operation cancelled.");
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        openInventories.remove(player.getUniqueId());
        selectedPacks.remove(player.getUniqueId());
        outputNames.remove(player.getUniqueId());
    }

    private void executeMerge(Player player, List<String> packs, String outputName) {
        List<File> packFiles = new ArrayList<>();
        for (String packName : packs) {
            File packFile = plugin.getResourcePacks().get(packName);
            if (packFile != null) {
                packFiles.add(packFile);
            }
        }

        if (packFiles.size() < 2) {
            player.sendMessage(ChatColor.RED + "Error: Not enough valid packs selected!");
            return;
        }

        player.sendMessage(ChatColor.YELLOW + "Merging " + packFiles.size() + " resource packs...");
        plugin.getServer().dispatchCommand(player, "mergepack " + outputName + " " + 
            String.join(" ", packs));
    }

    private void previewMerge(Player player, List<String> packs) {
        player.sendMessage(ChatColor.YELLOW + "Selected packs to merge:");
        for (String pack : packs) {
            File packFile = plugin.getResourcePacks().get(pack);
            if (packFile != null) {
                player.sendMessage(ChatColor.GRAY + "- " + pack + " (" + 
                    (packFile.length() / 1024) + "KB)");
            }
        }
        player.sendMessage(ChatColor.YELLOW + "Use the merge button to combine these packs.");
    }
} 