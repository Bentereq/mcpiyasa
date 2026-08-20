package com.mcpiyasa.gui;

import com.mcpiyasa.compat.Materials;
import com.mcpiyasa.config.Messages;
import com.mcpiyasa.config.ParsedItems;
import com.mcpiyasa.config.PluginSettings;
import com.mcpiyasa.engine.ItemDef;
import com.mcpiyasa.engine.Quote;
import com.mcpiyasa.engine.TradeSide;
import com.mcpiyasa.market.BukkitInventory;
import com.mcpiyasa.market.MarketService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Tek bir urunun gercek marjinal alis ve satis tekliflerini gosterir. */
public final class ItemMenu {
    public static final int BUY_ONE_SLOT = 10;
    public static final int BUY_SIXTEEN_SLOT = 11;
    public static final int BUY_SIXTY_FOUR_SLOT = 12;
    public static final int ITEM_SLOT = 13;
    public static final int SELL_ONE_SLOT = 14;
    public static final int SELL_SIXTEEN_SLOT = 15;
    public static final int SELL_SIXTY_FOUR_SLOT = 16;
    public static final int BACK_SLOT = 18;
    public static final int SELL_ALL_SLOT = 22;

    private static final int SIZE = 27;

    private ItemMenu() {
    }

    public static void open(Player player, String itemId,
                            PluginSettings settings, Messages messages,
                            MarketService marketService,
                            CategoryMenu.Change24hLookup change24hLookup) {
        open(player, itemId, null, 0, settings, messages, marketService,
            change24hLookup);
    }

    public static void open(Player player, String itemId,
                            ParsedItems parsedItems, PluginSettings settings,
                            Messages messages, MarketService marketService,
                            CategoryMenu.Change24hLookup change24hLookup) {
        String category = parsedItems == null
            ? null : parsedItems.itemCategory.get(itemId);
        int page = 0;
        if (parsedItems != null && category != null) {
            // Sayfa, kategori menusunun gosterdigi aktif listeye gore hesaplanir.
            List<String> categoryItems =
                parsedItems.activeCategoryItems(category);
            int index = categoryItems.indexOf(itemId);
            page = index < 0 ? 0 : index / Icons.PAGE_SIZE;
        }
        open(player, itemId, category, page, parsedItems, settings, messages,
            marketService, change24hLookup);
    }

    public static void open(Player player, String itemId, String category,
                            int page, PluginSettings settings,
                            Messages messages, MarketService marketService,
                            CategoryMenu.Change24hLookup change24hLookup) {
        open(player, itemId, category, page, null, settings, messages,
            marketService,
            change24hLookup);
    }

    public static void open(Player player, String itemId, String category,
                            int page, ParsedItems parsedItems,
                            PluginSettings settings, Messages messages,
                            MarketService marketService,
                            CategoryMenu.Change24hLookup change24hLookup) {
        open(player, itemId, category, page, parsedItems, settings, messages,
            marketService, change24hLookup,
            category == null ? MenuType.MAIN : MenuType.CATEGORY);
    }

    static void openFromMovers(
            Player player, String itemId, String category, int page,
            ParsedItems parsedItems, PluginSettings settings,
            Messages messages, MarketService marketService,
            CategoryMenu.Change24hLookup change24hLookup) {
        open(player, itemId, category, page, parsedItems, settings, messages,
            marketService, change24hLookup, MenuType.MOVERS);
    }

    static void reopen(Player player, MenuHolder holder,
                       ParsedItems parsedItems, PluginSettings settings,
                       Messages messages, MarketService marketService,
                       CategoryMenu.Change24hLookup change24hLookup) {
        open(player, holder.itemId, holder.category, holder.page, parsedItems,
            settings, messages, marketService, change24hLookup, holder.origin);
    }

    private static void open(Player player, String itemId, String category,
                             int page, ParsedItems parsedItems,
                             PluginSettings settings, Messages messages,
                             MarketService marketService,
                             CategoryMenu.Change24hLookup change24hLookup,
                             MenuType origin) {
        MenuHolder holder = new MenuHolder(
            MenuType.ITEM, category, page, itemId, origin, null);
        Inventory inventory = Bukkit.createInventory(
            holder,
            SIZE,
            messages.get("gui.item-baslik", Collections.singletonMap(
                "item", itemId)));
        holder.setInventory(inventory);

        Material itemMaterial = Materials.resolve(itemId);
        if (itemMaterial == null) {
            itemMaterial = Material.STONE;
        }
        CategoryMenu.Change24hLookup renderLookup = settings.kriptoGosterim
            ? change24hLookup : null;
        ItemDef item = parsedItems == null
            ? null : parsedItems.items.get(itemId);
        inventory.setItem(ITEM_SLOT, itemIcon(
            player, itemMaterial, itemId, settings, messages, marketService,
            renderLookup, item));

        addTradeButton(inventory, BUY_ONE_SLOT, 1, TradeSide.BUY,
            player, settings, messages, marketService, itemId, item);
        addTradeButton(inventory, BUY_SIXTEEN_SLOT, 16, TradeSide.BUY,
            player, settings, messages, marketService, itemId, item);
        addTradeButton(inventory, BUY_SIXTY_FOUR_SLOT, 64, TradeSide.BUY,
            player, settings, messages, marketService, itemId, item);
        addTradeButton(inventory, SELL_ONE_SLOT, 1, TradeSide.SELL,
            player, settings, messages, marketService, itemId, item);
        addTradeButton(inventory, SELL_SIXTEEN_SLOT, 16, TradeSide.SELL,
            player, settings, messages, marketService, itemId, item);
        addTradeButton(inventory, SELL_SIXTY_FOUR_SLOT, 64, TradeSide.SELL,
            player, settings, messages, marketService, itemId, item);

        int inventoryAmount = inventoryCount(player, itemId);
        int sellAmount = effectiveAmount(
            inventoryAmount, settings.engineParams.maxTradeAmount);
        List<String> sellAllLore = new ArrayList<String>();
        boolean sellDirectionEnabled = item == null || item.satisAcik;
        boolean sellEnabled = sellAllEnabled(
            sellDirectionEnabled, inventoryAmount,
            settings.engineParams.maxTradeAmount);
        String sellAllName;
        if (sellEnabled) {
            Map<String, String> sellVars = new java.util.LinkedHashMap<String, String>();
            sellVars.put("adet", Integer.toString(sellAmount));
            sellVars.put("tutar", Icons.money(quoteTotal(
                marketService, player, itemId, sellAmount, TradeSide.SELL)));
            sellAllLore.add(messages.get("gui.sell-all-lore", sellVars));
            sellAllName = inventoryAmount > sellAmount
                ? messages.get("gui.azami-sat", Collections.singletonMap(
                    "adet", Integer.toString(sellAmount)))
                : messages.get("gui.tumunu-sat");
        } else if (!sellDirectionEnabled) {
            sellAllLore.add(messages.get("gui.satis-kapali"));
            sellAllName = messages.get("gui.satis-kapali");
        } else if (inventoryAmount == 0) {
            Map<String, String> emptyVars = Collections.singletonMap(
                "item", itemId);
            sellAllName = messages.get("gui.tumunu-sat-bos", emptyVars);
            sellAllLore.add(sellAllName);
        } else {
            sellAllLore.add(messages.get("gui.satacak-item-yok"));
            sellAllName = messages.get("gui.satacak-item-yok");
        }
        inventory.setItem(SELL_ALL_SLOT, namedWithLore(
            new ItemStack(sellEnabled
                ? Material.HOPPER : Material.GRAY_STAINED_GLASS_PANE),
            sellAllName,
            sellAllLore));
        inventory.setItem(BACK_SLOT, namedWithLore(
            new ItemStack(Material.BARRIER), messages.get("gui.geri"),
            Collections.<String>emptyList()));
        player.openInventory(inventory);
    }

    static TradeSide sideAt(int slot) {
        if (slot == BUY_ONE_SLOT || slot == BUY_SIXTEEN_SLOT
                || slot == BUY_SIXTY_FOUR_SLOT) {
            return TradeSide.BUY;
        }
        if (slot == SELL_ONE_SLOT || slot == SELL_SIXTEEN_SLOT
                || slot == SELL_SIXTY_FOUR_SLOT || slot == SELL_ALL_SLOT) {
            return TradeSide.SELL;
        }
        return null;
    }

    static int requestedAmountAt(int slot, Player player, String itemId,
                                 int maxTradeAmount) {
        int requested;
        if (slot == BUY_ONE_SLOT || slot == SELL_ONE_SLOT) {
            requested = 1;
        } else if (slot == BUY_SIXTEEN_SLOT || slot == SELL_SIXTEEN_SLOT) {
            requested = 16;
        } else if (slot == BUY_SIXTY_FOUR_SLOT
                || slot == SELL_SIXTY_FOUR_SLOT) {
            requested = 64;
        } else if (slot == SELL_ALL_SLOT) {
            requested = inventoryCount(player, itemId);
        } else {
            return -1;
        }

        return effectiveAmount(requested, maxTradeAmount);
    }

    static int inventoryCount(Player player, String itemId) {
        if (player == null) {
            return 0;
        }
        Material material = Materials.resolve(itemId);
        if (material == null) {
            return 0;
        }
        return BukkitInventory.count(player.getInventory(), material);
    }

    static int effectiveAmount(int requested, int maxTradeAmount) {
        int positiveMax = Math.max(0, maxTradeAmount);
        return Math.min(Math.max(0, requested), positiveMax);
    }

    static boolean sellAllEnabled(boolean sellDirectionEnabled,
                                  int inventoryAmount,
                                  int maxTradeAmount) {
        return sellDirectionEnabled
            && effectiveAmount(inventoryAmount, maxTradeAmount) > 0;
    }

    /**
     * Bos envanterle tiklanan tumunu-sat butonunun mesaj anahtari. Satis yonu
     * kapaliysa envanter bosluguna degil kapali yone isaret edilir.
     */
    static String sellAllBosMesajAnahtari(ItemDef item) {
        return item != null && !item.isTradeEnabled(TradeSide.SELL)
            ? "islem.satis-kapali"
            : "islem.yetersiz-item";
    }

    /**
     * Envanterde ayni materyalden yalniz meta tasiyan yiginlar varsa
     * "yeterli item yok" yerine kuralin kendisini soyler.
     */
    static String sellAllBosMesajAnahtari(ItemDef item, Player player,
                                          String itemId) {
        String key = sellAllBosMesajAnahtari(item);
        if (!"islem.yetersiz-item".equals(key)) {
            return key;
        }
        return hasModifiedStack(player, itemId)
            ? "islem.degistirilmis-item" : key;
    }

    static boolean hasModifiedStack(Player player, String itemId) {
        if (player == null) {
            return false;
        }
        Material material = Materials.resolve(itemId);
        if (material == null) {
            return false;
        }
        try {
            return BukkitInventory.hasModifiedStack(
                player.getInventory(), material);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static void addTradeButton(Inventory inventory, int slot,
                                       int amount, TradeSide side,
                                       Player player,
                                       PluginSettings settings,
                                       Messages messages,
                                       MarketService marketService,
                                       String itemId, ItemDef item) {
        boolean enabled = item == null || item.isTradeEnabled(side);
        Material material = enabled
            ? side == TradeSide.BUY
                ? Material.LIME_STAINED_GLASS_PANE
                : Material.RED_STAINED_GLASS_PANE
            : Material.GRAY_STAINED_GLASS_PANE;
        String sideName = messages.get(enabled
            ? side == TradeSide.BUY ? "gui.alis" : "gui.satis"
            : side == TradeSide.BUY ? "gui.alis-kapali" : "gui.satis-kapali");
        int quotedAmount = effectiveAmount(
            amount, settings.engineParams.maxTradeAmount);
        String amountName = messages.get(
            "gui.miktar", Collections.singletonMap(
                "adet", Integer.toString(quotedAmount)));
        List<String> lore;
        if (enabled) {
            Map<String, String> vars = new java.util.LinkedHashMap<String, String>();
            vars.put("islem", sideName);
            vars.put("adet", Integer.toString(quotedAmount));
            vars.put("tutar", Icons.money(quoteTotal(
                marketService, player, itemId, quotedAmount, side)));
            lore = Collections.singletonList(
                messages.get("gui.trade-lore", vars));
        } else {
            lore = Collections.singletonList(sideName);
        }
        inventory.setItem(slot, namedWithLore(
            new ItemStack(material, quotedAmount), sideName + " " + amountName,
            lore));
    }

    private static double quoteTotal(MarketService marketService,
                                     Player player,
                                     String itemId,
                                     int amount, TradeSide side) {
        if (amount <= 0) {
            return 0.0;
        }
        try {
            return marketService.preview(
                player, itemId, amount, side).totalPrice;
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            return 0.0;
        }
    }

    private static ItemStack itemIcon(
            Player player, Material material, String itemId,
            PluginSettings settings, Messages messages,
            MarketService marketService,
            CategoryMenu.Change24hLookup change24hLookup, ItemDef item) {
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
        if (settings.kriptoGosterim && change24hLookup != null) {
            try {
                double change = change24hLookup.percent(itemId);
                Map<String, String> vars = Collections.singletonMap(
                    "degisim",
                    Change24h.arrow(change) + " " + Icons.percent(change));
                lore.add(messages.get("gui.degisim-24s", vars));
            } catch (IllegalArgumentException | IllegalStateException ignored) {
                // Bilinmeyen item icin degisim satiri eklenmez.
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
            // Bilinmeyen item icin fiyat satiri eklenmez.
        }
    }

    private static ItemStack namedWithLore(ItemStack icon, String displayName,
                                           List<String> lore) {
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
