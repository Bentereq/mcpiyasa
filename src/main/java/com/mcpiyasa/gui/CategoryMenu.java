package com.mcpiyasa.gui;

import com.mcpiyasa.compat.Materials;
import com.mcpiyasa.config.Messages;
import com.mcpiyasa.config.ParsedItems;
import com.mcpiyasa.config.PluginSettings;
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

public final class CategoryMenu {
    private static final int SIZE = 54;

    private CategoryMenu() {
    }

    public static void open(Player player, String category, int page,
                            ParsedItems parsedItems, PluginSettings settings,
                            Messages messages, MarketService marketService,
                            Change24hLookup change24hLookup) {
        // Devre disi urunler oyuncuya gorunmez: aktif suzme yolunu kullaniriz,
        // boylece sayfa ve slot indisleri suzulmus listeye gore hesaplanir.
        List<String> categoryItems = parsedItems.activeCategoryItems(category);
        int safePage = Icons.clampPage(page, categoryItems.size());
        MenuHolder holder = new MenuHolder(
            MenuType.CATEGORY, category, safePage, null);
        Inventory inventory = Bukkit.createInventory(
            holder,
            SIZE,
            messages.get("gui.kategori-baslik", Collections.singletonMap(
                "kategori", MainMenu.categoryLabel(messages, category))));
        holder.setInventory(inventory);

        List<String> pageItems = Icons.pageOf(categoryItems, safePage);
        Change24hLookup renderLookup = settings.kriptoGosterim
            ? Change24h.memoized(change24hLookup, pageItems) : null;
        for (int slot = 0; slot < pageItems.size(); slot++) {
            String itemId = pageItems.get(slot);
            Material material = Materials.resolve(itemId);
            if (material != null) {
                inventory.setItem(slot, itemIcon(
                    player, material, itemId, settings, messages, marketService,
                    renderLookup, parsedItems.items.get(itemId)));
            }
        }

        inventory.setItem(Icons.BACK_SLOT,
            named(Material.BARRIER, messages.get("gui.geri")));
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

    private static ItemStack itemIcon(Player player,
                                      Material material, String itemId,
                                      PluginSettings settings,
                                      Messages messages,
                                      MarketService marketService,
                                      Change24hLookup change24hLookup,
                                      ItemDef item) {
        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) {
            return icon;
        }

        List<String> lore = new ArrayList<String>();
        addDirectionLore(
            lore, player, itemId, item, TradeSide.BUY, messages, marketService);
        addDirectionLore(
            lore, player, itemId, item, TradeSide.SELL, messages, marketService);
        if (settings.kriptoGosterim) {
            try {
                double change = change24hLookup == null
                    ? 0.0 : change24hLookup.percent(itemId);
                lore.add(messages.get(
                    "gui.degisim-24s",
                    Collections.singletonMap(
                        "degisim",
                        Change24h.arrow(change) + " " + Icons.percent(change))));
            } catch (IllegalArgumentException | IllegalStateException ignored) {
                // Bilinmeyen item icin fiyat degisimi de gosterilemez.
            }
        }
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private static void addDirectionLore(List<String> lore,
                                         Player player,
                                         String itemId,
                                         ItemDef item,
                                         TradeSide side,
                                         Messages messages,
                                         MarketService marketService) {
        if (item != null && !item.isTradeEnabled(side)) {
            lore.add(messages.get(side == TradeSide.BUY
                ? "gui.alis-kapali" : "gui.satis-kapali"));
            return;
        }
        try {
            Quote quote = marketService.preview(player, itemId, 1, side);
            String label = messages.get(
                side == TradeSide.BUY ? "gui.alis" : "gui.satis");
            lore.add(label + " " + Icons.money(quote.unitAvg));
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            // Gecersiz bir item diger kategori ikonlarini gizlememeli.
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

    public interface Change24hLookup {
        double percent(String itemId);
    }
}
