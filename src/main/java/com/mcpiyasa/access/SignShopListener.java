package com.mcpiyasa.access;

import com.mcpiyasa.compat.Materials;
import com.mcpiyasa.compat.SignCompat;
import com.mcpiyasa.compat.Text;
import com.mcpiyasa.config.Messages;
import com.mcpiyasa.config.ParsedItems;
import com.mcpiyasa.config.PluginSettings;
import com.mcpiyasa.engine.Quote;
import com.mcpiyasa.engine.ItemDef;
import com.mcpiyasa.engine.TradeSide;
import com.mcpiyasa.gui.CategoryMenu;
import com.mcpiyasa.gui.Icons;
import com.mcpiyasa.gui.ItemMenu;
import com.mcpiyasa.market.MarketService;

import java.util.Collections;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Market tabelalarini olusturur ve item menusune yonlendirir.
 * Gecerli etkilesimlerde fiyat satirlari yeni tekliflerle tazelenir.
 * Dinleyici yalniz {@code settings.tabelaMarket} acikken kaydedilir.
 */
public final class SignShopListener implements Listener {
    private static final String ADMIN_PERMISSION = "mcpiyasa.admin";

    private final ParsedItems parsedItems;
    private final PluginSettings settings;
    private final Messages messages;
    private final MarketService marketService;
    private final CategoryMenu.Change24hLookup change24hLookup;

    public SignShopListener(ParsedItems parsedItems, PluginSettings settings,
                            Messages messages, MarketService marketService) {
        this(parsedItems, settings, messages, marketService, null);
    }

    public SignShopListener(ParsedItems parsedItems, PluginSettings settings,
                            Messages messages, MarketService marketService,
                            CategoryMenu.Change24hLookup change24hLookup) {
        if (parsedItems == null || settings == null || messages == null
                || marketService == null) {
            throw new IllegalArgumentException(
                "SignShopListener bagimliliklari null olamaz");
        }
        this.parsedItems = parsedItems;
        this.settings = settings;
        this.messages = messages;
        this.marketService = marketService;
        this.change24hLookup = change24hLookup;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!SignParser.isMarketHeader(event.getLine(0))) {
            return;
        }
        if (!event.getPlayer().hasPermission(ADMIN_PERMISSION)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(messages.chat("komut.yetki-yok"));
            return;
        }

        String itemId = canonicalItemId(SignParser.parse(
            event.getLine(0), event.getLine(1)));
        if (itemId == null) {
            event.setCancelled(true);
            String rawItem = event.getLine(1) == null
                ? "" : event.getLine(1).trim();
            event.getPlayer().sendMessage(messages.chat(
                "islem.bilinmeyen-item",
                Collections.singletonMap("item", rawItem)));
            return;
        }
        if (isInactive(itemId)) {
            // Devre disi urune tabela baglanmaz.
            event.setCancelled(true);
            event.getPlayer().sendMessage(
                messages.chat("islem.urun-devre-disi",
                    Collections.singletonMap("item", itemId)));
            return;
        }

        Quote buy;
        Quote sell;
        try {
            buy = marketService.preview(
                event.getPlayer(), itemId, 1, TradeSide.BUY);
            sell = marketService.preview(
                event.getPlayer(), itemId, 1, TradeSide.SELL);
        } catch (IllegalArgumentException ignored) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(messages.chat("islem.hata"));
            return;
        }

        event.setLine(0, Text.color("&1[Market]"));
        event.setLine(1, itemId);
        event.setLine(2, directionLine(itemId, TradeSide.BUY, buy));
        event.setLine(3, directionLine(itemId, TradeSide.SELL, sell));
    }

    @EventHandler(ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        boolean protectionAllowsBlockUse =
            event.useInteractedBlock() != Event.Result.DENY;
        // Koruma eklentileri blok kullanimini reddettiyse bu dinleyici
        // ustune yazmaz.
        if (!protectionAllowsBlockUse) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null) {
            return;
        }
        BlockState state = event.getClickedBlock().getState();
        if (!(state instanceof Sign)) {
            return;
        }
        Sign sign = (Sign) state;
        String header = SignCompat.getLine(sign, 0);
        if (!SignParser.isMarketHeader(header)) {
            return;
        }

        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        String rawItem = SignCompat.getLine(sign, 1);
        String itemId = canonicalItemId(SignParser.parse(header, rawItem));
        if (itemId == null) {
            invalidateDeadSign(sign, event.getPlayer(), messages, rawItem);
            return;
        }
        if (!event.getPlayer().hasPermission("mcpiyasa.use")) {
            event.getPlayer().sendMessage(messages.chat("komut.yetki-yok"));
            return;
        }
        if (isInactive(itemId)) {
            // Devre disi urun: bilinmeyen item gibi, menu acilmaz.
            event.getPlayer().sendMessage(
                messages.chat("islem.urun-devre-disi",
                    Collections.singletonMap("item", itemId)));
            return;
        }

        Quote buy;
        Quote sell;
        try {
            buy = marketService.preview(
                event.getPlayer(), itemId, 1, TradeSide.BUY);
            sell = marketService.preview(
                event.getPlayer(), itemId, 1, TradeSide.SELL);
        } catch (IllegalArgumentException ignored) {
            event.getPlayer().sendMessage(messages.chat("islem.hata"));
            return;
        }

        SignCompat.setLine(
            sign, 2, directionLine(itemId, TradeSide.BUY, buy));
        SignCompat.setLine(
            sign, 3, directionLine(itemId, TradeSide.SELL, sell));
        sign.update();
        ItemMenu.open(
            event.getPlayer(), itemId, parsedItems, settings, messages,
            marketService, change24hLookup);
    }

    static void invalidateDeadSign(Sign sign, org.bukkit.entity.Player player,
                                   Messages messages, String rawItem) {
        player.sendMessage(messages.chat(
            "islem.bilinmeyen-item", Collections.singletonMap(
                "item", rawItem == null ? "" : rawItem.trim())));
        SignCompat.setLine(sign, 2, "");
        SignCompat.setLine(sign, 3, "");
        sign.update();
    }

    /** Kanonik itemId markette var ama {@code aktif: false} ise devre disidir. */
    private boolean isInactive(String itemId) {
        ItemDef item = parsedItems.items.get(itemId);
        return item != null && !item.active;
    }

    private String canonicalItemId(String parsedItemId) {
        Material material = Materials.resolve(parsedItemId);
        if (material == null
                || !parsedItems.items.containsKey(material.name())) {
            return null;
        }
        return material.name();
    }

    private String priceLine(String key, double price) {
        return messages.get(key, Collections.singletonMap(
            "fiyat", Icons.money(price)));
    }

    private String directionLine(String itemId, TradeSide side, Quote quote) {
        ItemDef item = parsedItems.items.get(itemId);
        if (quote == null || item != null && !item.isTradeEnabled(side)) {
            return messages.get(side == TradeSide.BUY
                ? "tabela.alis-kapali" : "tabela.satis-kapali");
        }
        return priceLine(
            side == TradeSide.BUY ? "tabela.alis" : "tabela.satis",
            quote.unitAvg
        );
    }
}
