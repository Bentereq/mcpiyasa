package com.mcpiyasa.gui;

import com.mcpiyasa.compat.Materials;
import com.mcpiyasa.config.Messages;
import com.mcpiyasa.config.ParsedItems;
import com.mcpiyasa.engine.GroupDef;
import com.mcpiyasa.engine.ItemDef;
import com.mcpiyasa.engine.Quote;
import com.mcpiyasa.engine.TradeSide;
import com.mcpiyasa.market.MarketService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Tek item'in tikla-yonet duzenleyicisi: kademeli fiyat dugmeleri, yon
 * ac/kapat, sifirla ve (onayli) silme. Sohbet kutusu olmadigi icin tum
 * eylemler tiklamayla yapilir; yazma yolu {@link AdminGuiActions}'tadir.
 */
public final class AdminItemMenu {
    private AdminItemMenu() {
    }

    public static void open(Player player, AdminGuiHost host, String category,
                            int page, String itemId, boolean confirmRemove) {
        ParsedItems items = host.items();
        Messages messages = host.messages();
        MarketService marketService = host.marketService();
        ItemDef item = items.items.get(itemId);
        GroupDef group = item == null ? null : items.groups.get(item.groupId);

        MenuHolder holder = new MenuHolder(
            MenuType.ADMIN_ITEM, category, page, itemId, confirmRemove);
        Inventory inventory = Bukkit.createInventory(
            holder,
            AdminLayout.ITEM_SIZE,
            messages.get("admin-gui.item-baslik", Collections.singletonMap(
                "item", itemId)));
        holder.setInventory(inventory);

        Material material = Materials.resolve(itemId);
        if (material == null) {
            material = Material.STONE;
        }
        inventory.setItem(AdminLayout.ITEM_ICON_SLOT, itemIcon(
            player, material, itemId, item, group, messages, marketService));

        boolean buyOpen = item == null || item.isTradeEnabled(TradeSide.BUY);
        boolean sellOpen = item == null || item.isTradeEnabled(TradeSide.SELL);

        inventory.setItem(AdminLayout.STEP_HALVE_SLOT, stepButton(
            Material.RED_STAINED_GLASS_PANE,
            messages.get("admin-gui.step-bol-iki"), messages));
        inventory.setItem(AdminLayout.STEP_MINUS_TEN_SLOT, stepButton(
            Material.RED_STAINED_GLASS_PANE,
            messages.get("admin-gui.step-eksi-yuzde"), messages));
        inventory.setItem(AdminLayout.STEP_MINUS_ONE_SLOT, stepButton(
            Material.RED_STAINED_GLASS_PANE,
            messages.get("admin-gui.step-eksi-bir"), messages));
        inventory.setItem(AdminLayout.STEP_PLUS_ONE_SLOT, stepButton(
            Material.LIME_STAINED_GLASS_PANE,
            messages.get("admin-gui.step-arti-bir"), messages));
        inventory.setItem(AdminLayout.STEP_PLUS_TEN_SLOT, stepButton(
            Material.LIME_STAINED_GLASS_PANE,
            messages.get("admin-gui.step-arti-yuzde"), messages));
        inventory.setItem(AdminLayout.STEP_DOUBLE_SLOT, stepButton(
            Material.LIME_STAINED_GLASS_PANE,
            messages.get("admin-gui.step-carp-iki"), messages));

        inventory.setItem(AdminLayout.TOGGLE_BUY_SLOT, toggle(
            buyOpen, messages.get("admin-gui.alis-toggle",
                Collections.singletonMap("durum", stateWord(buyOpen, messages))),
            messages));
        inventory.setItem(AdminLayout.TOGGLE_SELL_SLOT, toggle(
            sellOpen, messages.get("admin-gui.satis-toggle",
                Collections.singletonMap("durum", stateWord(sellOpen, messages))),
            messages));

        boolean active = item == null || item.active;
        inventory.setItem(AdminLayout.TOGGLE_ACTIVE_SLOT,
            activeToggle(active, messages));

        inventory.setItem(AdminLayout.ITEM_BACK_SLOT,
            named(Material.BARRIER, messages.get("admin-gui.geri"),
                Collections.<String>emptyList()));
        inventory.setItem(AdminLayout.ITEM_RESET_SLOT, named(
            Material.CLOCK, messages.get("admin-gui.sifirla-buton"),
            Collections.singletonList(messages.get("admin-gui.sifirla-buton-lore"))));
        inventory.setItem(AdminLayout.ITEM_REMOVE_SLOT, named(
            Material.TNT,
            messages.get(confirmRemove ? "admin-gui.sil-onayla" : "admin-gui.sil"),
            Collections.singletonList(messages.get(
                confirmRemove ? "admin-gui.sil-onayla-lore" : "admin-gui.sil-lore"))));
        player.openInventory(inventory);
    }

    private static ItemStack itemIcon(Player player, Material material,
                                      String itemId, ItemDef item,
                                      GroupDef group, Messages messages,
                                      MarketService marketService) {
        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) {
            return icon;
        }
        List<String> lore = new ArrayList<String>();
        if (item != null && group != null) {
            lore.add(messages.get("admin-gui.taban-fiyat",
                Collections.singletonMap(
                    "fiyat", Icons.money(group.basePrice * item.weight))));
        }
        lore.add(liveLine(
            player, itemId, item, TradeSide.BUY, messages, marketService,
            "admin-gui.canli-alis"));
        lore.add(liveLine(
            player, itemId, item, TradeSide.SELL, messages, marketService,
            "admin-gui.canli-satis"));
        boolean buyOpen = item == null || item.isTradeEnabled(TradeSide.BUY);
        boolean sellOpen = item == null || item.isTradeEnabled(TradeSide.SELL);
        lore.add(messages.get("admin-gui.alis-durum", Collections.singletonMap(
            "durum", stateWord(buyOpen, messages))));
        lore.add(messages.get("admin-gui.satis-durum", Collections.singletonMap(
            "durum", stateWord(sellOpen, messages))));
        boolean active = item == null || item.active;
        lore.add(messages.get("admin-gui.aktif-durum", Collections.singletonMap(
            "durum", activeWord(active, messages))));
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private static String liveLine(Player player, String itemId, ItemDef item,
                                   TradeSide side, Messages messages,
                                   MarketService marketService, String key) {
        if (item != null && !item.isTradeEnabled(side)) {
            return messages.get(side == TradeSide.BUY
                ? "gui.alis-kapali" : "gui.satis-kapali");
        }
        try {
            Quote quote = marketService.preview(player, itemId, 1, side);
            String value = quote == null ? "-" : Icons.money(quote.unitAvg);
            return messages.get(key, Collections.singletonMap("fiyat", value));
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            return messages.get(key, Collections.singletonMap("fiyat", "-"));
        }
    }

    private static ItemStack stepButton(Material material, String name,
                                        Messages messages) {
        return named(material, name,
            Collections.singletonList(messages.get("admin-gui.step-lore")));
    }

    private static ItemStack toggle(boolean open, String name,
                                    Messages messages) {
        return named(open
                ? Material.LIME_STAINED_GLASS_PANE
                : Material.RED_STAINED_GLASS_PANE,
            name, Collections.singletonList(messages.get("admin-gui.toggle-lore")));
    }

    private static String stateWord(boolean open, Messages messages) {
        return messages.get(open ? "admin-gui.acik" : "admin-gui.kapali");
    }

    private static String activeWord(boolean active, Messages messages) {
        return messages.get(active ? "admin-gui.aktif" : "admin-gui.pasif");
    }

    /**
     * Aktifse "Devre Disi Birak" (kirmizi), degilse "Aktiflestir" (yesil):
     * dugme metni yapilacak eylemi, rengi de eylemin turunu yansitir.
     */
    private static ItemStack activeToggle(boolean active, Messages messages) {
        String name = messages.get(active
            ? "admin-gui.devre-disi-birak" : "admin-gui.aktiflestir");
        Material material = active
            ? Material.RED_STAINED_GLASS_PANE
            : Material.LIME_STAINED_GLASS_PANE;
        return named(material, name,
            Collections.singletonList(messages.get("admin-gui.aktif-toggle-lore")));
    }

    private static ItemStack named(Material material, String displayName,
                                   List<String> lore) {
        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            icon.setItemMeta(meta);
        }
        return icon;
    }
}
