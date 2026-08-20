package com.mcpiyasa.gui;

import com.mcpiyasa.compat.Materials;
import com.mcpiyasa.config.Messages;
import com.mcpiyasa.config.ParsedItems;
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
 * Bir kategorinin item'larini sayfali listeler; her ikon oyuncu menusuyle ayni
 * NEUTRAL onizleme yoluyla canli alis/satis birim fiyatini gosterir (commit
 * yok). Tiklama item duzenleyicisini acar.
 */
public final class AdminCategoryMenu {
    private AdminCategoryMenu() {
    }

    public static void open(Player player, AdminGuiHost host, String category,
                            int page) {
        ParsedItems items = host.items();
        Messages messages = host.messages();
        MarketService marketService = host.marketService();

        List<String> categoryItems = items.categoryItems.get(category);
        if (categoryItems == null) {
            categoryItems = Collections.emptyList();
        }
        int safePage = Icons.clampPage(page, categoryItems.size());
        MenuHolder holder = new MenuHolder(
            MenuType.ADMIN_CATEGORY, category, safePage, null);
        Inventory inventory = Bukkit.createInventory(
            holder,
            AdminLayout.CATEGORY_SIZE,
            messages.get("admin-gui.kategori-baslik", Collections.singletonMap(
                "kategori", MainMenu.categoryLabel(messages, category))));
        holder.setInventory(inventory);

        List<String> pageItems = Icons.pageOf(categoryItems, safePage);
        for (int slot = 0; slot < pageItems.size(); slot++) {
            String itemId = pageItems.get(slot);
            Material material = Materials.resolve(itemId);
            if (material != null) {
                inventory.setItem(slot, itemIcon(
                    player, material, itemId, messages, marketService,
                    items.items.get(itemId)));
            }
        }

        inventory.setItem(Icons.BACK_SLOT,
            named(Material.BARRIER, messages.get("admin-gui.geri")));
        if (Icons.hasPreviousPage(safePage)) {
            inventory.setItem(Icons.PREVIOUS_SLOT,
                named(Material.ARROW, messages.get("gui.onceki-sayfa")));
        }
        inventory.setItem(Icons.PAGE_INDICATOR_SLOT,
            named(Material.PAPER,
                Icons.pageIndicator(safePage, categoryItems.size())));
        if (Icons.hasNextPage(safePage, categoryItems.size())) {
            inventory.setItem(Icons.NEXT_SLOT,
                named(Material.ARROW, messages.get("gui.sonraki-sayfa")));
        }
        player.openInventory(inventory);
    }

    private static ItemStack itemIcon(Player player, Material material,
                                      String itemId, Messages messages,
                                      MarketService marketService, ItemDef item) {
        boolean inactive = item != null && !item.active;
        // Devre disi urun admin listesinde de gorunur ama gorsel olarak
        // isaretlenir: ayirt edici (gri) cam + adinda "(devre disi)" eki.
        ItemStack icon = new ItemStack(
            inactive ? Material.GRAY_STAINED_GLASS_PANE : material);
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) {
            return icon;
        }
        if (inactive) {
            meta.setDisplayName(
                itemId + " " + messages.get("admin.urun-devre-disi-etiket"));
        }
        List<String> lore = new ArrayList<String>();
        if (inactive) {
            lore.add(messages.get("admin.urun-devre-disi-etiket"));
        }
        lore.add(directionLine(
            player, itemId, item, TradeSide.BUY, messages, marketService,
            "admin-gui.canli-alis"));
        lore.add(directionLine(
            player, itemId, item, TradeSide.SELL, messages, marketService,
            "admin-gui.canli-satis"));
        lore.add(messages.get("admin-gui.duzenle-ipucu"));
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private static String directionLine(Player player, String itemId,
                                        ItemDef item, TradeSide side,
                                        Messages messages,
                                        MarketService marketService,
                                        String key) {
        if (item != null && !item.isTradeEnabled(side)) {
            return messages.get(side == TradeSide.BUY
                ? "gui.alis-kapali" : "gui.satis-kapali");
        }
        try {
            Quote quote = marketService.preview(player, itemId, 1, side);
            if (quote == null) {
                return messages.get(side == TradeSide.BUY
                    ? "gui.alis-kapali" : "gui.satis-kapali");
            }
            return messages.get(key, Collections.singletonMap(
                "fiyat", Icons.money(quote.unitAvg)));
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            return messages.get(key, Collections.singletonMap("fiyat", "-"));
        }
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
