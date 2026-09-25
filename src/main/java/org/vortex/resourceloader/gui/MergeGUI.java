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
import org.vortex.resourceloader.util.MessageManager;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MergeGUI implements Listener {
    private final Resourceloader plugin;
    private final Map<UUID, List<String>> selectedPacks;
    private final Map<UUID, String> outputNames;
    private final Map<UUID, Inventory> openInventories;
    private static final int MAX_PACKS = 45; // Maximum number of packs that can be displayed
    private static final Sound SELECT_SOUND = Sound.BLOCK_NOTE_BLOCK_PLING;
    private static final Sound ERROR_SOUND = Sound.BLOCK_NOTE_BLOCK_BASS;
    private static final Sound SUCCESS_SOUND = Sound.ENTITY_PLAYER_LEVELUP;

    public MergeGUI(Resourceloader plugin) {
        this.plugin = plugin;
        this.selectedPacks = new ConcurrentHashMap<>();
        this.outputNames = new ConcurrentHashMap<>();
        this.openInventories = new ConcurrentHashMap<>();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private MessageManager messages() {
        return plugin.getMessageManager();
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
            player.sendMessage(messages().formatMessage("gui.too-many-packs", "max", MAX_PACKS));
            return;
        }

        // Create inventory with appropriate size
        int size = Math.min(54, ((availablePacks.size() / 9) + 1) * 9 + 9);
        Inventory inv = Bukkit.createInventory(null, size, messages().getMessageNoPrefix("gui.title"));

        // Store player data
        outputNames.put(player.getUniqueId(), outputName);
        selectedPacks.put(player.getUniqueId(), new ArrayList<>());
        openInventories.put(player.getUniqueId(), inv);

        try {
            // Add available packs
            int slot = 0;
            for (Map.Entry<String, File> entry : availablePacks) {
                ItemStack item = createPackItem(entry.getKey(), entry.getValue());
                inv.setItem(slot++, item);
            }

            // Add control buttons at the bottom row
            int bottomRow = size - 9;
            inv.setItem(bottomRow + 0, createControlItem(Material.LIME_WOOL,
                messages().getMessageNoPrefix("gui.merge-button"),
                Arrays.asList(messages().getMessageNoPrefix("gui.merge-button-lore"),
                    messages().formatMessageNoPrefix("gui.output-lore", "pack", outputName))));
            inv.setItem(bottomRow + 1, createControlItem(Material.YELLOW_WOOL,
                messages().getMessageNoPrefix("gui.preview-button"),
                Collections.singletonList(messages().getMessageNoPrefix("gui.preview-button-lore"))));
            inv.setItem(bottomRow + 8, createControlItem(Material.RED_WOOL,
                messages().getMessageNoPrefix("gui.cancel-button"),
                Collections.singletonList(messages().getMessageNoPrefix("gui.cancel-button-lore"))));

            player.openInventory(inv);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create merge GUI: " + e.getMessage());
            player.sendMessage(messages().getMessage("gui.open-failed"));
            cleanup(player.getUniqueId());
        }
    }

    private ItemStack createPackItem(String name, File file) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + name);
            
            List<String> lore = new ArrayList<>();
            lore.add(messages().formatMessageNoPrefix("gui.pack-file", "file", file.getName()));
            lore.add(messages().formatMessageNoPrefix("gui.pack-size", "size", formatFileSize(file.length())));
            lore.add("");
            lore.add(messages().getMessageNoPrefix("gui.click-to-select"));
            
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
            meta.setDisplayName(name);
            meta.setLore(new ArrayList<>(lore));
            
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory mergeInventory = openInventories.get(player.getUniqueId());
        if (mergeInventory == null || !mergeInventory.equals(event.getInventory())) return;

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
            player.sendMessage(messages().getMessage("gui.cancelled"));
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
        lore.set(lore.size() - 1, messages().getMessageNoPrefix(
            selectedList.contains(packName) ? "gui.selected" : "gui.click-to-select"));
        meta.setLore(lore);
        clicked.setItemMeta(meta);
    }

    private void handleMergeAction(Player player, List<String> selectedList, String outputName) {
        if (selectedList.size() < 2) {
            player.sendMessage(messages().getMessage("gui.select-more-merge"));
            player.playSound(player.getLocation(), ERROR_SOUND, 1.0f, 0.8f);
            return;
        }
        player.closeInventory();
        player.playSound(player.getLocation(), SUCCESS_SOUND, 1.0f, 1.0f);
        executeMerge(player, selectedList, outputName);
    }

    private void handlePreviewAction(Player player, List<String> selectedList) {
        if (selectedList.size() < 2) {
            player.sendMessage(messages().getMessage("gui.select-more-preview"));
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
                player.sendMessage(messages().getMessage("gui.not-enough-valid"));
                player.playSound(player.getLocation(), ERROR_SOUND, 1.0f, 0.8f);
                return;
            }

            player.sendMessage(messages().formatMessage("gui.merging", "count", packFiles.size()));
            plugin.getServer().dispatchCommand(player, "mergepack " + outputName + " " + 
                String.join(" ", packs));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to execute merge: " + e.getMessage());
            player.sendMessage(messages().getMessage("gui.merge-failed"));
            player.playSound(player.getLocation(), ERROR_SOUND, 1.0f, 0.8f);
        }
    }

    private void previewMerge(Player player, List<String> packs) {
        try {
            player.sendMessage(messages().getMessage("gui.preview-header"));
            long totalSize = 0;
            for (String pack : packs) {
                File packFile = plugin.getResourcePacks().get(pack);
                if (packFile != null && packFile.exists()) {
                    long size = packFile.length();
                    totalSize += size;
                    player.sendMessage(messages().formatMessageNoPrefix("gui.preview-entry",
                        "pack", pack, "size", formatFileSize(size)));
                }
            }
            player.sendMessage(messages().formatMessageNoPrefix("gui.preview-total", "size", formatFileSize(totalSize)));
            player.sendMessage(messages().getMessageNoPrefix("gui.preview-hint"));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to preview merge: " + e.getMessage());
            player.sendMessage(messages().getMessage("gui.preview-failed"));
            player.playSound(player.getLocation(), ERROR_SOUND, 1.0f, 0.8f);
        }
    }
} 