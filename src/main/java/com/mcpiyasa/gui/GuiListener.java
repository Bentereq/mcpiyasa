package com.mcpiyasa.gui;

import com.mcpiyasa.config.CategoryDef;
import com.mcpiyasa.config.ItemNames;
import com.mcpiyasa.config.Messages;
import com.mcpiyasa.config.ParsedItems;
import com.mcpiyasa.config.PluginSettings;
import com.mcpiyasa.engine.GroupDef;
import com.mcpiyasa.engine.ItemDef;
import com.mcpiyasa.engine.PriceEngine;
import com.mcpiyasa.engine.TradeSide;
import com.mcpiyasa.market.Clock;
import com.mcpiyasa.market.MarketResult;
import com.mcpiyasa.market.MarketService;
import com.mcpiyasa.storage.SnapshotRepo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public final class GuiListener implements Listener {
    private static final String ADMIN_PERMISSION = "mcpiyasa.admin";

    /**
     * Menu acmayi tik olayinin disina tasiyan kanca. Varsayilan uygulama
     * hemen calistirir; canli sunucuda 1 tik gecikmeli goreve baglanir.
     */
    public interface MenuScheduler {
        void open(Runnable openTask);
    }

    /** Test ve eski yapicilar icin dogrudan calistiran zamanlayici. */
    public static final MenuScheduler IMMEDIATE = new MenuScheduler() {
        @Override
        public void open(Runnable openTask) {
            openTask.run();
        }
    };

    private final ParsedItems parsedItems;
    private final PluginSettings settings;
    private final Messages messages;
    private final ItemNames itemNames;
    private final PriceEngine engine;
    private final MarketService marketService;
    private final CategoryMenu.Change24hLookup change24hLookup;
    private final MenuScheduler menuScheduler;
    /** Admin menuleri icin canli veri/eylem kaynagi; null ise admin GUI kapali. */
    private final AdminGuiHost adminHost;

    public GuiListener(ParsedItems parsedItems, PluginSettings settings,
                       Messages messages, PriceEngine engine) {
        this(parsedItems, settings, messages, engine, null,
            (CategoryMenu.Change24hLookup) null);
    }

    public GuiListener(ParsedItems parsedItems, PluginSettings settings,
                       Messages messages, PriceEngine engine,
                       MarketService marketService, SnapshotRepo snapshotRepo,
                       Clock clock) {
        this(parsedItems, settings, messages, engine, marketService,
            new Change24h(engine, snapshotRepo, clock));
    }

    public GuiListener(ParsedItems parsedItems, PluginSettings settings,
                       Messages messages, PriceEngine engine,
                       MarketService marketService,
                       CategoryMenu.Change24hLookup change24hLookup) {
        this(parsedItems, settings, messages, engine, marketService,
            change24hLookup, IMMEDIATE);
    }

    public GuiListener(ParsedItems parsedItems, PluginSettings settings,
                       Messages messages, PriceEngine engine,
                       MarketService marketService,
                       CategoryMenu.Change24hLookup change24hLookup,
                       MenuScheduler menuScheduler) {
        this(parsedItems, settings, messages, engine, marketService,
            change24hLookup, menuScheduler, null);
    }

    public GuiListener(ParsedItems parsedItems, PluginSettings settings,
                       Messages messages, PriceEngine engine,
                       MarketService marketService,
                       CategoryMenu.Change24hLookup change24hLookup,
                       MenuScheduler menuScheduler,
                       AdminGuiHost adminHost) {
        this(parsedItems, settings, messages, ItemNames.empty(), engine,
            marketService, change24hLookup, menuScheduler, adminHost);
    }

    public GuiListener(ParsedItems parsedItems, PluginSettings settings,
                       Messages messages, ItemNames itemNames,
                       PriceEngine engine,
                       MarketService marketService,
                       CategoryMenu.Change24hLookup change24hLookup,
                       MenuScheduler menuScheduler,
                       AdminGuiHost adminHost) {
        this.parsedItems = parsedItems;
        this.settings = settings;
        this.messages = messages;
        this.itemNames = itemNames == null ? ItemNames.empty() : itemNames;
        this.engine = engine;
        this.marketService = marketService;
        this.change24hLookup = change24hLookup;
        this.menuScheduler = menuScheduler == null ? IMMEDIATE : menuScheduler;
        this.adminHost = adminHost;
    }

    /**
     * Yalniz tek/shift sol-sag tiklar bir eyleme donusur. Cift tik, sayi
     * tusu, off-hand takasi ve surukleme iptal edilip yok sayilir; aksi
     * halde tek bir cift tik iki islem tetikler.
     */
    static boolean isRoutableClick(ClickType click) {
        return click == ClickType.LEFT
            || click == ClickType.RIGHT
            || click == ClickType.SHIFT_LEFT
            || click == ClickType.SHIFT_RIGHT;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!isRoutableClick(event.getClick())) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()
                || !(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        MenuHolder holder = (MenuHolder) event.getInventory().getHolder();

        if (isAdminMenu(holder.type)) {
            // Yetki her tikta yeniden denetlenir; sizmis bir admin holder'a
            // tiklayan yetkisiz oyuncuda hicbir sey olmaz (tik zaten iptal).
            if (adminHost == null
                    || !player.hasPermission(ADMIN_PERMISSION)) {
                return;
            }
            routeAdmin(player, holder, slot, event.getClick());
            return;
        }

        switch (holder.type) {
            case MAIN:
                openCategoryFromMain(player, slot);
                return;
            case CATEGORY:
                routeCategory(player, holder, slot);
                return;
            case ITEM:
                routeItem(player, holder, slot);
                return;
            case MOVERS:
                routeMovers(player, holder, slot);
                return;
            default:
                return;
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    private void openCategoryFromMain(final Player player, int slot) {
        if (settings.kriptoGosterim && slot == Icons.MOVERS_SLOT) {
            menuScheduler.open(new Runnable() {
                @Override public void run() {
                    if (!player.isOnline()) {
                        return;
                    }
                    MoversMenu.open(
                        player, parsedItems, settings, messages,
                        itemNames, change24hLookup);
                }
            });
            return;
        }
        int categoryIndex = Icons.categoryIndex(
            slot, settings.kriptoGosterim);
        if (categoryIndex < 0 || categoryIndex >= parsedItems.categories.size()) {
            return;
        }
        CategoryDef category = parsedItems.categories.get(categoryIndex);
        openCategory(player, category.id, 0);
    }

    private void routeCategory(final Player player, final MenuHolder holder,
                               int slot) {
        if (Icons.isCategoryItemSlot(slot)) {
            List<String> itemIds = categoryItems(holder.category);
            List<String> pageItems = Icons.pageOf(itemIds, holder.page);
            if (slot < pageItems.size()) {
                final String itemId = pageItems.get(slot);
                menuScheduler.open(new Runnable() {
                    @Override public void run() {
                        if (!player.isOnline()) {
                            return;
                        }
                        ItemMenu.open(
                            player, itemId, holder.category, holder.page,
                            parsedItems, settings, messages, itemNames,
                            marketService, change24hLookup);
                    }
                });
            }
            return;
        }
        if (slot == Icons.BACK_SLOT) {
            openMain(player);
            return;
        }

        List<String> itemIds = categoryItems(holder.category);
        if (slot == Icons.PREVIOUS_SLOT
                && Icons.hasPreviousPage(holder.page)) {
            openCategory(player, holder.category, holder.page - 1);
        } else if (slot == Icons.NEXT_SLOT
                && Icons.hasNextPage(holder.page, itemIds.size())) {
            openCategory(player, holder.category, holder.page + 1);
        }
    }

    private void routeItem(final Player player, final MenuHolder holder,
                           int slot) {
        if (slot == ItemMenu.BACK_SLOT) {
            if (holder.origin == MenuType.MOVERS) {
                menuScheduler.open(new Runnable() {
                    @Override public void run() {
                        if (!player.isOnline()) {
                            return;
                        }
                        MoversMenu.open(
                            player, parsedItems, settings, messages,
                            itemNames, change24hLookup);
                    }
                });
            } else if (holder.category == null) {
                openMain(player);
            } else {
                openCategory(player, holder.category, holder.page);
            }
            return;
        }

        TradeSide side = ItemMenu.sideAt(slot);
        if (side == null || marketService == null) {
            return;
        }
        if (slot == ItemMenu.SELL_ALL_SLOT
                && ItemMenu.inventoryCount(player, holder.itemId) == 0) {
            ItemDef item = parsedItems.items.get(holder.itemId);
            Map<String, String> vars = new LinkedHashMap<String, String>();
            vars.put("item", itemNames.of(holder.itemId));
            vars.put("adet", "1");
            player.sendMessage(messages.chat(
                ItemMenu.sellAllBosMesajAnahtari(item, player, holder.itemId),
                vars));
            return;
        }
        int amount = ItemMenu.requestedAmountAt(
            slot, player, holder.itemId,
            settings.engineParams.maxTradeAmount);
        MarketResult result = marketService.trade(
            player, holder.itemId, amount, side);
        sendResult(player, result);
        // Takas sonrasi otomatik yeniden acilis: 1 tik sonra oyuncu hala
        // cevrimici ve ayni urunun menusunu acik tutuyorsa yenilenir. ESC
        // ile kapattiysa (ust envanter artik MenuHolder degil ya da baska
        // urun) kendiliginden geri acilmaz.
        menuScheduler.open(new Runnable() {
            @Override public void run() {
                if (!player.isOnline()) {
                    return;
                }
                if (!isSameItemMenuOpen(player, holder.itemId)) {
                    return;
                }
                ItemMenu.reopen(
                    player, holder, parsedItems, settings, messages,
                    itemNames, marketService, change24hLookup);
            }
        });
    }

    private void routeMovers(final Player player, MenuHolder holder,
                             int slot) {
        if (!settings.kriptoGosterim) {
            return;
        }
        if (slot == MoversMenu.BACK_SLOT) {
            openMain(player);
            return;
        }
        final String itemId = MoversMenu.itemAtSlot(holder, slot);
        if (itemId == null) {
            return;
        }
        final String category = parsedItems.itemCategory.get(itemId);
        final int page = pageForItem(category, itemId);
        menuScheduler.open(new Runnable() {
            @Override public void run() {
                if (!player.isOnline()) {
                    return;
                }
                ItemMenu.openFromMovers(
                    player, itemId, category, page, parsedItems, settings,
                    messages, itemNames, marketService, change24hLookup);
            }
        });
    }

    private void openMain(final Player player) {
        menuScheduler.open(new Runnable() {
            @Override public void run() {
                if (!player.isOnline()) {
                    return;
                }
                MainMenu.open(player, parsedItems, settings, messages);
            }
        });
    }

    private void openCategory(final Player player, final String category,
                              final int page) {
        menuScheduler.open(new Runnable() {
            @Override public void run() {
                if (!player.isOnline()) {
                    return;
                }
                CategoryMenu.open(
                    player, category, page, parsedItems, settings, messages,
                    marketService, change24hLookup);
            }
        });
    }

    // --- Admin GUI yonlendirmesi (tumu mcpiyasa.admin kapili) ---

    static boolean isAdminMenu(MenuType type) {
        return type == MenuType.ADMIN_MAIN
            || type == MenuType.ADMIN_CATEGORY
            || type == MenuType.ADMIN_ITEM;
    }

    private void routeAdmin(Player player, MenuHolder holder, int slot,
                            ClickType click) {
        switch (holder.type) {
            case ADMIN_MAIN:
                routeAdminMain(player, slot);
                return;
            case ADMIN_CATEGORY:
                routeAdminCategory(player, holder, slot);
                return;
            case ADMIN_ITEM:
                routeAdminItem(player, holder, slot, click);
                return;
            default:
                return;
        }
    }

    private void routeAdminMain(Player player, int slot) {
        if (slot == AdminLayout.RELOAD_SLOT) {
            adminHost.actions().reload(player);
            openAdminMain(player);
            return;
        }
        if (slot == AdminLayout.STATUS_BUTTON_SLOT) {
            adminHost.actions().status(player);
            return;
        }
        if (slot == AdminLayout.ADD_HELD_SLOT) {
            String added = adminHost.actions().addHeldItem(player);
            if (added != null) {
                openAdminItem(
                    player, adminHost.items().itemCategory.get(added), 0,
                    added, false);
            }
            return;
        }
        if (slot == AdminLayout.RESET_INFO_SLOT) {
            return;
        }
        int categoryIndex = AdminLayout.mainCategoryIndex(slot);
        List<CategoryDef> categories = adminHost.items().categories;
        if (categoryIndex >= 0 && categoryIndex < categories.size()) {
            openAdminCategory(player, categories.get(categoryIndex).id, 0);
        }
    }

    private void routeAdminCategory(Player player, MenuHolder holder, int slot) {
        if (Icons.isCategoryItemSlot(slot)) {
            List<String> itemIds = adminCategoryItems(holder.category);
            List<String> pageItems = Icons.pageOf(itemIds, holder.page);
            if (slot < pageItems.size()) {
                openAdminItem(
                    player, holder.category, holder.page,
                    pageItems.get(slot), false);
            }
            return;
        }
        if (slot == Icons.BACK_SLOT) {
            openAdminMain(player);
            return;
        }
        List<String> itemIds = adminCategoryItems(holder.category);
        if (slot == Icons.PREVIOUS_SLOT && Icons.hasPreviousPage(holder.page)) {
            openAdminCategory(player, holder.category, holder.page - 1);
        } else if (slot == Icons.NEXT_SLOT
                && Icons.hasNextPage(holder.page, itemIds.size())) {
            openAdminCategory(player, holder.category, holder.page + 1);
        }
    }

    private void routeAdminItem(Player player, MenuHolder holder, int slot,
                                ClickType click) {
        if (slot == AdminLayout.ITEM_BACK_SLOT) {
            if (holder.category == null) {
                openAdminMain(player);
            } else {
                openAdminCategory(player, holder.category, holder.page);
            }
            return;
        }
        PriceStep step = AdminLayout.priceStepAt(slot);
        if (step != null) {
            double current = currentBaseUnit(holder.itemId);
            if (current > 0.0) {
                adminHost.actions().setBaseUnitPrice(
                    player, holder.itemId, step.apply(current));
            }
            openAdminItem(
                player, holder.category, holder.page, holder.itemId, false);
            return;
        }
        if (slot == AdminLayout.TOGGLE_BUY_SLOT) {
            adminHost.actions().toggleDirection(
                player, holder.itemId, TradeSide.BUY);
            openAdminItem(
                player, holder.category, holder.page, holder.itemId, false);
            return;
        }
        if (slot == AdminLayout.TOGGLE_SELL_SLOT) {
            adminHost.actions().toggleDirection(
                player, holder.itemId, TradeSide.SELL);
            openAdminItem(
                player, holder.category, holder.page, holder.itemId, false);
            return;
        }
        if (slot == AdminLayout.TOGGLE_ACTIVE_SLOT) {
            adminHost.actions().toggleActive(player, holder.itemId);
            openAdminItem(
                player, holder.category, holder.page, holder.itemId, false);
            return;
        }
        if (slot == AdminLayout.ITEM_RESET_SLOT) {
            if (isShiftClick(click)) {
                adminHost.actions().resetItem(player, holder.itemId);
                openAdminItem(
                    player, holder.category, holder.page, holder.itemId, false);
            }
            return;
        }
        if (slot == AdminLayout.ITEM_REMOVE_SLOT) {
            routeAdminRemove(player, holder, click);
        }
    }

    /** Silme yalniz shift-tik ile; ilk shift onay ister, ikincisi siler. */
    private void routeAdminRemove(Player player, MenuHolder holder,
                                  ClickType click) {
        if (!isShiftClick(click)) {
            return;
        }
        if (!holder.adminConfirm) {
            openAdminItem(
                player, holder.category, holder.page, holder.itemId, true);
            return;
        }
        boolean removed = adminHost.actions().removeItem(player, holder.itemId);
        if (removed) {
            if (holder.category == null) {
                openAdminMain(player);
            } else {
                openAdminCategory(player, holder.category, holder.page);
            }
        } else {
            openAdminItem(
                player, holder.category, holder.page, holder.itemId, false);
        }
    }

    private static boolean isShiftClick(ClickType click) {
        return click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT;
    }

    private double currentBaseUnit(String itemId) {
        ParsedItems items = adminHost.items();
        ItemDef item = items.items.get(itemId);
        GroupDef group = item == null ? null : items.groups.get(item.groupId);
        return item == null || group == null
            ? 0.0 : group.basePrice * item.weight;
    }

    private List<String> adminCategoryItems(String category) {
        List<String> itemIds = adminHost.items().categoryItems.get(category);
        return itemIds == null ? Collections.<String>emptyList() : itemIds;
    }

    private void openAdminMain(final Player player) {
        menuScheduler.open(new Runnable() {
            @Override public void run() {
                if (player.isOnline()) {
                    AdminMenu.open(player, adminHost);
                }
            }
        });
    }

    private void openAdminCategory(final Player player, final String category,
                                   final int page) {
        menuScheduler.open(new Runnable() {
            @Override public void run() {
                if (player.isOnline()) {
                    AdminCategoryMenu.open(player, adminHost, category, page);
                }
            }
        });
    }

    private void openAdminItem(final Player player, final String category,
                               final int page, final String itemId,
                               final boolean confirm) {
        menuScheduler.open(new Runnable() {
            @Override public void run() {
                if (!player.isOnline()) {
                    return;
                }
                if (adminHost.items().items.get(itemId) == null) {
                    // Item silinmis/yok: kategoriye ya da ana ekrana don.
                    if (category == null) {
                        AdminMenu.open(player, adminHost);
                    } else {
                        AdminCategoryMenu.open(player, adminHost, category, page);
                    }
                    return;
                }
                AdminItemMenu.open(
                    player, adminHost, category, page, itemId, confirm);
            }
        });
    }

    /**
     * Oyuncunun su an acik ust envanteri, beklenen urunun ITEM menusu mu?
     * Takas sonrasi otomatik yeniden acilis yalniz bu durumda yapilir; ESC
     * ile kapatan oyuncuya menu kendiliginden geri acilmaz.
     */
    private static boolean isSameItemMenuOpen(Player player,
                                              String expectedItemId) {
        if (player.getOpenInventory() == null
                || player.getOpenInventory().getTopInventory() == null) {
            return false;
        }
        return shouldReopenAfterTrade(
            player.getOpenInventory().getTopInventory().getHolder(),
            expectedItemId);
    }

    /**
     * Takas sonrasi yeniden acilis kararinin saf hali: ust envanterin
     * sahibi ayni urunun ITEM menusuyse true. Oyuncu ESC ile kapattiysa
     * (holder artik MenuHolder degil) ya da baska bir menu/urun acildiysa
     * false. Zamanlayiciya bagli olmadigi icin dogrudan test edilebilir.
     */
    static boolean shouldReopenAfterTrade(InventoryHolder topHolder,
                                          String expectedItemId) {
        if (!(topHolder instanceof MenuHolder)) {
            return false;
        }
        MenuHolder menuHolder = (MenuHolder) topHolder;
        return menuHolder.type == MenuType.ITEM
            && Objects.equals(menuHolder.itemId, expectedItemId);
    }

    private void sendResult(Player player, MarketResult result) {
        if (result == null) {
            return;
        }
        if (result.outcome == null && result.messageKey == null) {
            return;
        }
        if (result.messageKey != null) {
            player.sendMessage(messages.chat(result.messageKey, result.vars));
        }
    }

    /**
     * Oyuncu kategori yonlendirmesi yalniz AKTIF urunleri gorur; kategori
     * menusuyle ayni suzme yolunu kullanir ki slot -> item eslemesi ve sayfa
     * siniri render ile birebir ortusun. Admin yolu {@link #adminCategoryItems}
     * ile ham listeyi kullanir.
     */
    private List<String> categoryItems(String category) {
        return parsedItems.activeCategoryItems(category);
    }

    private int pageForItem(String category, String itemId) {
        int index = categoryItems(category).indexOf(itemId);
        return index < 0 ? 0 : index / Icons.PAGE_SIZE;
    }
}
