package com.mcpiyasa.gui;

import com.mcpiyasa.compat.Materials;
import com.mcpiyasa.config.Messages;
import com.mcpiyasa.config.ParsedItems;
import com.mcpiyasa.config.PluginSettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** En cok yukselen ve dusen urunleri iki sirada gosterir. */
public final class MoversMenu {
    public static final int BACK_SLOT = 49;
    public static final int GAINERS_HEADER_SLOT = 18;
    public static final int LOSERS_HEADER_SLOT = 44;
    private static final int SIZE = 54;
    private static final int GAINERS_LIMIT = 9;
    private static final int LOSERS_LIMIT = 8;

    private MoversMenu() {
    }

    public static void open(Player player, ParsedItems parsedItems,
                            PluginSettings settings, Messages messages,
                            CategoryMenu.Change24hLookup change24hLookup) {
        if (!settings.kriptoGosterim) {
            return;
        }

        CategoryMenu.Change24hLookup renderLookup = Change24h.memoized(
            change24hLookup, parsedItems.items.keySet());
        List<ChangeEntry> descending = sortedChanges(
            parsedItems, renderLookup);
        List<String> moversOrder = new ArrayList<String>();
        for (ChangeEntry entry : descending) {
            moversOrder.add(entry.itemId);
        }

        MenuHolder holder = new MenuHolder(
            MenuType.MOVERS, null, 0, null, moversOrder);
        Inventory inventory = Bukkit.createInventory(
            holder, SIZE, messages.get("gui.hareketliler-baslik"));
        holder.setInventory(inventory);

        inventory.setItem(GAINERS_HEADER_SLOT, named(
            Material.LIME_STAINED_GLASS_PANE,
            messages.get("gui.yukselenler-baslik")));
        inventory.setItem(LOSERS_HEADER_SLOT, named(
            Material.RED_STAINED_GLASS_PANE,
            messages.get("gui.dusenler-baslik")));

        for (int index = 0;
                index < Math.min(GAINERS_LIMIT, descending.size()); index++) {
            inventory.setItem(index, icon(
                descending.get(index), messages));
        }

        int loserCount = loserCount(descending.size());
        for (int rank = 0; rank < loserCount; rank++) {
            inventory.setItem(Icons.loserSlot(rank), icon(
                descending.get(descending.size() - 1 - rank), messages));
        }
        inventory.setItem(BACK_SLOT, named(
            Material.BARRIER, messages.get("gui.geri")));
        player.openInventory(inventory);
    }

    static String itemAtSlot(MenuHolder holder, int slot) {
        if (holder == null || holder.moversOrder == null
                || slot == BACK_SLOT) {
            return null;
        }
        List<String> order = holder.moversOrder;
        if (slot >= 0 && slot < GAINERS_LIMIT && slot < order.size()) {
            return order.get(slot);
        }
        int loserRank = Icons.loserRank(slot);
        if (loserRank >= 0 && loserRank < loserCount(order.size())) {
            return order.get(order.size() - 1 - loserRank);
        }
        return null;
    }

    private static int loserCount(int total) {
        int gainers = Math.min(GAINERS_LIMIT, Math.max(0, total));
        return Math.min(LOSERS_LIMIT, Math.max(0, total - gainers));
    }

    private static List<ChangeEntry> sortedChanges(
            ParsedItems parsedItems,
            CategoryMenu.Change24hLookup change24hLookup) {
        List<ChangeEntry> changes = new ArrayList<ChangeEntry>();
        for (String itemId : parsedItems.items.keySet()) {
            com.mcpiyasa.engine.ItemDef def = parsedItems.items.get(itemId);
            if (def != null && !def.active) {
                // Devre disi urunler hareketliler listesinde gorunmez.
                continue;
            }
            double percent = 0.0;
            if (change24hLookup != null) {
                try {
                    percent = change24hLookup.percent(itemId);
                } catch (IllegalArgumentException | IllegalStateException ignored) {
                    percent = 0.0;
                }
            }
            changes.add(new ChangeEntry(itemId, percent));
        }
        Collections.sort(changes, new Comparator<ChangeEntry>() {
            @Override
            public int compare(ChangeEntry left, ChangeEntry right) {
                int byPercent = Double.compare(right.percent, left.percent);
                return byPercent != 0
                    ? byPercent : left.itemId.compareTo(right.itemId);
            }
        });
        return changes;
    }

    private static ItemStack icon(ChangeEntry entry, Messages messages) {
        Material material = Materials.resolve(entry.itemId);
        if (material == null) {
            material = Material.STONE;
        }
        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setLore(Collections.singletonList(messages.get(
                "gui.degisim-24s",
                Collections.singletonMap(
                    "degisim",
                    Change24h.arrow(entry.percent) + " "
                        + Icons.percent(entry.percent)))));
            icon.setItemMeta(meta);
        }
        return icon;
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

    private static final class ChangeEntry {
        private final String itemId;
        private final double percent;

        private ChangeEntry(String itemId, double percent) {
            this.itemId = itemId;
            this.percent = percent;
        }
    }
}
