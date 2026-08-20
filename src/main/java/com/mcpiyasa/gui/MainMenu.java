package com.mcpiyasa.gui;

import com.mcpiyasa.compat.Materials;
import com.mcpiyasa.config.CategoryDef;
import com.mcpiyasa.config.Messages;
import com.mcpiyasa.config.ParsedItems;
import com.mcpiyasa.config.PluginSettings;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class MainMenu {
    private static final int SIZE = 27;

    private MainMenu() {
    }

    public static void open(Player player, ParsedItems parsedItems,
                            PluginSettings settings, Messages messages) {
        MenuHolder holder = new MenuHolder(MenuType.MAIN, null, 0, null);
        Inventory inventory = Bukkit.createInventory(
            holder, SIZE, messages.get("guimenu-baslik"));
        holder.setInventory(inventory);

        for (int index = 0; index < parsedItems.categories.size(); index++) {
            int slot = Icons.categorySlot(index, settings.kriptoGosterim);
            if (slot >= inventory.getSize()) {
                continue;
            }
            CategoryDef category = parsedItems.categories.get(index);
            Material material = Materials.resolve(category.iconMaterial);
            if (material == null) {
                material = Material.STONE;
            }
            inventory.setItem(slot, named(
                material,
                categoryLabel(messages, category.id)));
        }

        if (settings.kriptoGosterim) {
            inventory.setItem(Icons.MOVERS_SLOT, named(
                Material.GOLD_INGOT, messages.get("gui.yukselenler-baslik")));
        }
        player.openInventory(inventory);
    }

    /**
     * Mesaj dosyasinda karsiligi olmayan (ornegin admin komutuyla yeni
     * eklenmis) kategori icin ham kimlige duser; oyuncuya
     * "!eksik-mesaj:" gostermez.
     */
    static String categoryLabel(Messages messages, String categoryId) {
        String label = messages.get("kategori." + categoryId);
        return label.startsWith("!eksik-mesaj:") ? categoryId : label;
    }

    private static ItemStack named(Material material, String displayName) {
        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            icon.setItemMeta(meta);
        }
        return icon;
    }
}
