package com.mcpiyasa.market;

import com.mcpiyasa.api.events.MarketPriceChangeEvent;
import com.mcpiyasa.api.events.MarketTradeEvent;
import com.mcpiyasa.config.ParsedItems;
import com.mcpiyasa.config.PluginSettings;
import com.mcpiyasa.diag.SafeMode;
import com.mcpiyasa.engine.GroupState;
import com.mcpiyasa.engine.ItemDef;
import com.mcpiyasa.engine.Money;
import com.mcpiyasa.engine.PriceEngine;
import com.mcpiyasa.engine.Quote;
import com.mcpiyasa.engine.TradeContext;
import com.mcpiyasa.engine.TradeSide;
import com.mcpiyasa.storage.AsyncWriter;
import com.mcpiyasa.storage.TradePersistenceRepo;
import com.mcpiyasa.storage.TxLogRepo;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** Bukkit girislerini saf fiyat motoru ve atomik islem yurutucusuna baglar. */
public final class MarketService {
    private static final Runnable NO_SAFE_MODE_NOTIFICATION = new Runnable() {
        @Override public void run() { }
    };

    private final PriceEngine engine;
    private final Map<String, ItemDef> items;
    private final PluginSettings settings;
    private final SignalTracker signalTracker;
    private final SafeMode safeMode;
    private final EconomyBridge economy;
    private final InventoryBridge inventory;
    private final Clock clock;
    private final TradePreHook preHook;
    private final AsyncWriter asyncWriter;
    private final TxLogRepo txLogRepo;
    private final TradePersistenceRepo tradePersistenceRepo;
    private final TradeExecutor tradeExecutor;
    private final Logger logger;
    private final Runnable safeModeNotification;
    private boolean inTrade;

    public MarketService(PriceEngine engine,
                         ParsedItems parsedItems,
                         PluginSettings settings,
                         SignalTracker signalTracker,
                         SafeMode safeMode,
                         EconomyBridge economy,
                         InventoryBridge inventory,
                         Clock clock,
                         AsyncWriter asyncWriter,
                         TxLogRepo txLogRepo,
                         TradePersistenceRepo tradePersistenceRepo,
                         Logger logger) {
        this(
            engine,
            parsedItems,
            settings,
            signalTracker,
            safeMode,
            economy,
            inventory,
            clock,
            TradePreHook.ALLOW_ALL,
            asyncWriter,
            txLogRepo,
            tradePersistenceRepo,
            logger,
            NO_SAFE_MODE_NOTIFICATION
        );
    }

    public MarketService(PriceEngine engine,
                         ParsedItems parsedItems,
                         PluginSettings settings,
                         SignalTracker signalTracker,
                         SafeMode safeMode,
                         EconomyBridge economy,
                         InventoryBridge inventory,
                         Clock clock,
                         TradePreHook preHook,
                         AsyncWriter asyncWriter,
                         TxLogRepo txLogRepo,
                         TradePersistenceRepo tradePersistenceRepo,
                         Logger logger) {
        this(
            engine,
            parsedItems,
            settings,
            signalTracker,
            safeMode,
            economy,
            inventory,
            clock,
            preHook,
            asyncWriter,
            txLogRepo,
            tradePersistenceRepo,
            logger,
            NO_SAFE_MODE_NOTIFICATION
        );
    }

    public MarketService(PriceEngine engine,
                         ParsedItems parsedItems,
                         PluginSettings settings,
                         SignalTracker signalTracker,
                         SafeMode safeMode,
                         EconomyBridge economy,
                         InventoryBridge inventory,
                         Clock clock,
                         TradePreHook preHook,
                         AsyncWriter asyncWriter,
                         TxLogRepo txLogRepo,
                         TradePersistenceRepo tradePersistenceRepo,
                         Logger logger,
                         Runnable safeModeNotification) {
        if (engine == null || parsedItems == null || settings == null
                || signalTracker == null || safeMode == null || economy == null
                || inventory == null || clock == null || asyncWriter == null
                || txLogRepo == null || tradePersistenceRepo == null
                || logger == null || safeModeNotification == null) {
            throw new IllegalArgumentException("MarketService bagimliliklari null olamaz");
        }
        this.engine = engine;
        this.items = new LinkedHashMap<String, ItemDef>(parsedItems.items);
        this.settings = settings;
        this.signalTracker = signalTracker;
        this.safeMode = safeMode;
        this.economy = economy;
        this.inventory = inventory;
        this.clock = clock;
        this.preHook = preHook == null ? TradePreHook.ALLOW_ALL : preHook;
        this.asyncWriter = asyncWriter;
        this.txLogRepo = txLogRepo;
        this.tradePersistenceRepo = tradePersistenceRepo;
        this.logger = logger;
        this.safeModeNotification = safeModeNotification;
        this.tradeExecutor = new TradeExecutor(logger);
    }

    /**
     * Oyuncusuz yuzeyler icin projected canli baglamla teklif olusturur.
     * Istenen yon kapaliysa {@code null} dondurur.
     */
    public Quote preview(String itemId, int amount, TradeSide side) {
        return preview((Player) null, itemId, amount, side);
    }

    /**
     * GUI ile trade'in ayni oyuncu-aware projected baglamini kullanir.
     * Istenen yon kapaliysa {@code null} dondurur.
     */
    public Quote preview(Player player,
                         String itemId,
                         int amount,
                         TradeSide side) {
        ItemDef item = items.get(itemId);
        if (item == null) {
            throw new IllegalArgumentException("Bilinmeyen item: " + itemId);
        }
        if (side == null) {
            throw new IllegalArgumentException("TradeSide null olamaz");
        }
        // Devre disi urun bilinmeyen item gibi ele alinir: teklif reddedilir.
        if (!item.active) {
            throw new IllegalArgumentException("Devre disi item: " + itemId);
        }
        if (!item.isTradeEnabled(side)) {
            return null;
        }
        int effectiveAmount = Math.min(
            amount, settings.engineParams.maxTradeAmount);
        if (effectiveAmount <= 0) {
            throw new IllegalArgumentException("Gecersiz adet: " + amount);
        }
        String playerId = player == null
            ? null : player.getUniqueId().toString();
        double projectedVolume = effectiveAmount * item.weight;
        TradeContext context = signalTracker.contextForTrade(
            item.groupId,
            playerId,
            projectedVolume,
            side == TradeSide.SELL,
            clock.slotIndex(),
            clock.slotFraction());
        return engine.quote(itemId, effectiveAmount, side, context);
    }

    public MarketResult trade(Player player,
                              String itemId,
                              int amount,
                              TradeSide side) {
        if (inTrade) {
            return rejectReentrantTrade(player, itemId, amount, side);
        }
        inTrade = true;
        try {
            return executeTrade(player, itemId, amount, side);
        } finally {
            inTrade = false;
        }
    }

    private MarketResult executeTrade(Player player,
                                      String itemId,
                                      int amount,
                                      TradeSide side) {
        if (!safeMode.tradingAllowed(settings.zorlaCalistir)) {
            return result(null, null, "market.bakimda", emptyVars());
        }

        ItemDef item = items.get(itemId);
        if (item == null) {
            return result(
                null,
                null,
                "islem.bilinmeyen-item",
                singleVar("item", String.valueOf(itemId))
            );
        }
        // Devre disi urun: yon bayraklariyla ayni choke point'te reddedilir,
        // oyuncuya "markette degil" mesaji doner (bilinmeyen item gibi).
        if (!item.active) {
            return result(
                null,
                null,
                "islem.urun-devre-disi",
                singleVar("item", item.id)
            );
        }
        if (amount <= 0) {
            return result(
                null,
                null,
                "islem.gecersiz-adet",
                singleVar("adet", String.valueOf(amount))
            );
        }
        if (side == null || player == null) {
            return result(null, null, "islem.hata", emptyVars());
        }
        if (isCreativeBlocked(player)) {
            return result(null, null, "islem.creative-kapali", emptyVars());
        }
        if (!item.isTradeEnabled(side)) {
            return result(
                null,
                null,
                side == TradeSide.BUY
                    ? "islem.alis-kapali" : "islem.satis-kapali",
                singleVar("item", item.id)
            );
        }

        int effectiveAmount = amount;
        if (effectiveAmount > settings.engineParams.maxTradeAmount) {
            effectiveAmount = settings.engineParams.maxTradeAmount;
        }

        Quote quote;
        try {
            quote = preview(player, itemId, effectiveAmount, side);
        } catch (IllegalArgumentException | ArithmeticException e) {
            // ArithmeticException: ek savunma katmani; PriceEngine kendi
            // tasma korumasini uygular (IllegalArgumentException firlatir),
            // burasi elle duzenlenmis items.yml gibi beklenmedik yollara
            // karsi ikinci bir agdir - zehirli fiyat menuyu/ticareti kiramaz.
            logger.warning("Fiyat teklifi olusturulamadi: itemId=" + itemId);
            return result(null, null, "islem.hata", emptyVars());
        }
        Map<String, String> vars = quoteVars(quote);
        if (!preHook.allow(player, quote)) {
            return result(null, quote, "islem.iptal-edildi", vars);
        }

        TradeOutcome outcome = tradeExecutor.execute(
            player.getUniqueId(),
            quote,
            economy,
            inventory
        );
        if (outcome == TradeOutcome.OK) {
            double oldMid = engine.midPrice(itemId);
            engine.commit(quote);
            signalTracker.onTrade(
                item.groupId,
                player.getUniqueId().toString(),
                effectiveAmount * item.weight,
                side == TradeSide.SELL
            );
            safeMode.reportTradeSuccess();
            persistSuccess(player, item, quote);
            fireSuccessEvents(player, quote, oldMid, engine.midPrice(itemId));
        } else {
            long nowMs = clock.nowMs();
            if (outcome == TradeOutcome.EKONOMI_HATASI
                    || outcome == TradeOutcome.ROLLBACK_HATASI) {
                if (safeMode.reportTradeError(nowMs)) {
                    logger.severe(
                        "Safe mode aktif code=" + safeMode.reason()
                            + " detail=" + safeMode.detail());
                    notifySafeModeSafely();
                }
            }
            persistFailure(player, item, quote, outcome, nowMs);
        }

        return result(
            outcome, quote, messageKey(outcome, side, player, item), vars);
    }

    /**
     * Creative kapisi varsayilan olarak aciktir; yalniz
     * {@code ozellikler.creative-ticaret: false} yazan sahip icin kapanir.
     */
    private boolean isCreativeBlocked(Player player) {
        if (settings.creativeTicaret) {
            return false;
        }
        try {
            return player.getGameMode() == GameMode.CREATIVE;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /**
     * Envanterde hic duz yigin yokken yalniz meta tasiyan yiginlar varsa
     * oyuncuya "yeterli item yok" yerine nedenini soyler.
     */
    private String messageKey(TradeOutcome outcome, TradeSide side,
                              Player player, ItemDef item) {
        if (outcome == TradeOutcome.YETERSIZ_ITEM && side == TradeSide.SELL
                && holdsOnlyModifiedStacks(player, item)) {
            return "islem.degistirilmis-item";
        }
        return messageKey(outcome, side);
    }

    private boolean holdsOnlyModifiedStacks(Player player, ItemDef item) {
        try {
            return inventory.count(player.getUniqueId(), item.id) == 0
                && inventory.hasModifiedStack(player.getUniqueId(), item.id);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private MarketResult rejectReentrantTrade(Player player,
                                              String itemId,
                                              int amount,
                                              TradeSide side) {
        logger.warning(
            "Reentrant trade reddedildi"
                + " player=" + (player == null
                    ? "null" : player.getUniqueId())
                + " itemId=" + itemId
                + " amount=" + amount
                + " side=" + side);
        if (safeMode.reportTradeError(clock.nowMs())) {
            logger.severe(
                "Safe mode aktif code=" + safeMode.reason()
                    + " detail=" + safeMode.detail());
            notifySafeModeSafely();
        }
        return result(
            TradeOutcome.EKONOMI_HATASI,
            null,
            "islem.hata",
            emptyVars());
    }

    private void notifySafeModeSafely() {
        try {
            safeModeNotification.run();
        } catch (RuntimeException | LinkageError failure) {
            logger.log(
                java.util.logging.Level.SEVERE,
                "Safe mode admin bildirimi basarisiz oldu",
                failure);
        }
    }

    private static void fireSuccessEvents(Player player,
                                          Quote quote,
                                          double oldMid,
                                          double newMid) {
        if (Bukkit.getServer() == null) {
            return;
        }
        Bukkit.getPluginManager().callEvent(new MarketTradeEvent(
            player,
            quote.itemId,
            quote.amount,
            quote.side,
            quote.totalPrice,
            TradeOutcome.OK
        ));
        if (Double.compare(oldMid, newMid) != 0) {
            Bukkit.getPluginManager().callEvent(new MarketPriceChangeEvent(
                quote.itemId,
                oldMid,
                newMid
            ));
        }
    }

    private void persistSuccess(Player player, ItemDef item, Quote quote) {
        final TradeRecord record = new TradeRecord(
            clock.nowMs(),
            player.getUniqueId(),
            item.id,
            item.groupId,
            quote.amount,
            quote.side.name(),
            quote.totalPrice,
            item.weight,
            TradeOutcome.OK.name()
        );
        GroupState state = engine.state(item.groupId);
        final String groupId = item.groupId;
        final double stock = state.stock;
        final double epsilon = state.epsilon;
        asyncWriter.submit(new Runnable() {
            @Override
            public void run() {
                tradePersistenceRepo.persistSuccess(
                    record, groupId, stock, epsilon);
            }
        });
    }

    private void persistFailure(Player player,
                                ItemDef item,
                                Quote quote,
                                TradeOutcome outcome,
                                long nowMs) {
        final TradeRecord record = new TradeRecord(
            nowMs,
            player.getUniqueId(),
            item.id,
            item.groupId,
            quote.amount,
            quote.side.name(),
            quote.totalPrice,
            item.weight,
            outcome.name()
        );
        asyncWriter.submit(new Runnable() {
            @Override
            public void run() {
                txLogRepo.insert(record);
            }
        });
    }

    private static String messageKey(TradeOutcome outcome, TradeSide side) {
        switch (outcome) {
            case OK:
                return side == TradeSide.BUY
                    ? "islem.alis-basarili"
                    : "islem.satis-basarili";
            case YETERSIZ_BAKIYE:
                return "islem.yetersiz-bakiye";
            case YETERSIZ_ITEM:
                return "islem.yetersiz-item";
            case ENVANTER_DOLU:
                return "islem.envanter-dolu";
            case ROLLBACK_HATASI:
            case EKONOMI_HATASI:
            default:
                return "islem.hata";
        }
    }

    private static Map<String, String> quoteVars(Quote quote) {
        Map<String, String> vars = new LinkedHashMap<String, String>();
        vars.put("adet", String.valueOf(quote.amount));
        vars.put("item", quote.itemId);
        vars.put(
            "tutar",
            String.format(Locale.ROOT, "%.2f", Money.round(quote.totalPrice))
        );
        return vars;
    }

    private static Map<String, String> singleVar(String key, String value) {
        Map<String, String> vars = new LinkedHashMap<String, String>();
        vars.put(key, value);
        return vars;
    }

    private static Map<String, String> emptyVars() {
        return Collections.emptyMap();
    }

    private static MarketResult result(TradeOutcome outcome,
                                       Quote quote,
                                       String messageKey,
                                       Map<String, String> vars) {
        return new MarketResult(outcome, quote, messageKey, vars);
    }
}
