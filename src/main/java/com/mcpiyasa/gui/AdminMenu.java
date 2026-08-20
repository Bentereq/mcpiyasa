package com.mcpiyasa.gui;

import com.mcpiyasa.compat.Materials;
import com.mcpiyasa.config.CategoryDef;
import com.mcpiyasa.config.Messages;
import com.mcpiyasa.config.ParsedItems;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Admin ana ekrani: durum ozeti + reload/durum/item-ekle/sifirla dugmeleri ve
 * duzenlenecek kategoriler. Tum veri her acilista canli {@link AdminGuiHost}'tan
 * okunur; reload sonrasi bayat kalmaz.
 */
public final class AdminMenu {
    private AdminMenu() {
    }

    public static void open(Player player, AdminGuiHost host) {
        ParsedItems items = host.items();
        Messages messages = host.messages();
        MenuHolder holder = new MenuHolder(MenuType.ADMIN_MAIN, null, 0, null);
        Inventory inventory = Bukkit.createInventory(
            holder, AdminLayout.MAIN_SIZE, messages.get("admin-gui.baslik"));
        holder.setInventory(inventory);

        inventory.setItem(AdminLayout.STATUS_SLOT, statusBook(host));
        inventory.setItem(AdminLayout.RELOAD_SLOT, button(
            Material.ANVIL, messages.get("admin-gui.reload"),
            messages.get("admin-gui.reload-lore")));
        inventory.setItem(AdminLayout.STATUS_BUTTON_SLOT, button(
            Material.PAPER, messages.get("admin-gui.durum-buton"),
            messages.get("admin-gui.durum-buton-lore")));
        inventory.setItem(AdminLayout.ADD_HELD_SLOT, button(
            Material.HOPPER, messages.get("admin-gui.item-ekle"),
            messages.get("admin-gui.item-ekle-lore")));
        inventory.setItem(AdminLayout.RESET_INFO_SLOT, button(
            Material.CLOCK, messages.get("admin-gui.sifirla-bilgi"),
            messages.get("admin-gui.sifirla-bilgi-lore")));

        for (int index = 0; index < items.categories.size(); index++) {
            int slot = AdminLayout.mainCategorySlot(index);
            if (slot < 0) {
                continue;
            }
            CategoryDef category = items.categories.get(index);
            Material material = Materials.resolve(category.iconMaterial);
            if (material == null) {
                material = Material.STONE;
            }
            inventory.setItem(slot, button(
                material,
                MainMenu.categoryLabel(messages, category.id),
                messages.get("admin-gui.kategori-ac")));
        }
        player.openInventory(inventory);
    }

    private static ItemStack statusBook(AdminGuiHost host) {
        ParsedItems items = host.items();
        Messages messages = host.messages();
        List<String> lore = new ArrayList<String>();
        lore.add(messages.get("admin-gui.durum-urunler",
            singletonVar("adet", Integer.toString(items.items.size()))));
        lore.add(messages.get("admin-gui.durum-gruplar",
            singletonVar("adet", Integer.toString(items.groups.size()))));
        lore.add(messages.get("admin-gui.durum-guvenli-mod", singletonVar(
            "durum", Boolean.toString(host.safeMode().isActive()))));
        lore.add(messages.get("admin-gui.durum-hassasiyet", singletonVar(
            "deger", String.format(
                Locale.ROOT, "%.2f", host.settings().hassasiyet))));
        return named(Material.BOOK, messages.get("admin-gui.durum-baslik"), lore);
    }

    private static ItemStack button(Material material, String name,
                                    String loreLine) {
        return named(material, name, Collections.singletonList(loreLine));
    }

    private static ItemStack named(Material material, String name,
                                   List<String> lore) {
        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private static Map<String, String> singletonVar(String key, String value) {
        return Collections.singletonMap(key, value);
    }
}
