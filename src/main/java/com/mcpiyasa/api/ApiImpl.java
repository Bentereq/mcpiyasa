package com.mcpiyasa.api;

import com.mcpiyasa.config.ParsedItems;
import com.mcpiyasa.engine.EngineParams;
import com.mcpiyasa.engine.ItemDef;
import com.mcpiyasa.engine.Money;
import com.mcpiyasa.engine.PriceEngine;
import com.mcpiyasa.engine.TradeSide;
import com.mcpiyasa.market.Clock;
import com.mcpiyasa.market.MarketResult;
import com.mcpiyasa.market.MarketService;
import com.mcpiyasa.market.SignalTracker;
import com.mcpiyasa.market.TradeOutcome;
import com.mcpiyasa.storage.SnapshotRepo;
import com.mcpiyasa.storage.TxLogRepo;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** MCPiyasa'nin ic servislerini kararlı gelistirici API'sine uyarlar. */
public final class ApiImpl implements MCPiyasaAPI {
    private final MarketService marketService;
    private final PriceEngine engine;
    private final Map<String, ItemDef> items;
    private final int maxTradeAmount;
    private final SnapshotRepo snapshotRepo;
    private final TxLogRepo txLogRepo;
    private final Clock clock;
    private final PrimaryThreadGuard primaryThreadGuard;

    public ApiImpl(MarketService marketService,
                   PriceEngine engine,
                   ParsedItems parsedItems,
                   EngineParams engineParams,
                   SignalTracker signalTracker,
                   SnapshotRepo snapshotRepo,
                   TxLogRepo txLogRepo,
                   Clock clock) {
        this(
            marketService,
            engine,
            parsedItems,
            engineParams,
            signalTracker,
            snapshotRepo,
            txLogRepo,
            clock,
            BukkitPrimaryThreadGuard.INSTANCE
        );
    }

    ApiImpl(MarketService marketService,
            PriceEngine engine,
            ParsedItems parsedItems,
            EngineParams engineParams,
            SignalTracker signalTracker,
            SnapshotRepo snapshotRepo,
            TxLogRepo txLogRepo,
            Clock clock,
            PrimaryThreadGuard primaryThreadGuard) {
        if (marketService == null || engine == null || parsedItems == null
                || engineParams == null
                || snapshotRepo == null || txLogRepo == null || clock == null
                || primaryThreadGuard == null) {
            throw new IllegalArgumentException("ApiImpl bagimliliklari null olamaz");
        }
        if (engineParams.maxTradeAmount < 1) {
            throw new IllegalArgumentException("maxTradeAmount en az 1 olmalidir");
        }
        this.marketService = marketService;
        this.engine = engine;
        this.items = new LinkedHashMap<String, ItemDef>(parsedItems.items);
        this.maxTradeAmount = engineParams.maxTradeAmount;
        this.snapshotRepo = snapshotRepo;
        this.txLogRepo = txLogRepo;
        this.clock = clock;
        this.primaryThreadGuard = primaryThreadGuard;
    }

    public ApiImpl(MarketService marketService,
                   PriceEngine engine,
                   ParsedItems parsedItems,
                   EngineParams engineParams,
                   SnapshotRepo snapshotRepo,
                   TxLogRepo txLogRepo,
                   Clock clock) {
        this(
            marketService,
            engine,
            parsedItems,
            engineParams,
            null,
            snapshotRepo,
            txLogRepo,
            clock
        );
    }

    /**
     * @throws IllegalStateException urun devre disi ({@code aktif: false}) ise;
     *     devre disi urun oyuncu yuzeylerindeki gibi ticaret disidir.
     */
    @Override
    public PriceQuoteDto getQuote(Material item, int amount, TradeSide side) {
        requirePrimaryThread("getQuote");
        ItemDef definition = requireItem(item);
        if (side == null) {
            throw new IllegalArgumentException("TradeSide null olamaz");
        }
        if (!definition.active) {
            throw new IllegalStateException("urun devre disi");
        }
        if (!definition.isTradeEnabled(side)) {
            throw new IllegalStateException("yon kapali");
        }
        int effectiveAmount = amount;
        if (effectiveAmount > maxTradeAmount) {
            effectiveAmount = maxTradeAmount;
        }
        com.mcpiyasa.engine.Quote quote = marketService.preview(
            definition.id, effectiveAmount, side);
        return new PriceQuoteDto(
            quote.itemId,
            quote.amount,
            quote.totalPrice,
            quote.unitAvg
        );
    }

    /**
     * @throws IllegalStateException urun devre disi ({@code aktif: false})
     *     ise; getQuote/trade ile ayni choke point.
     */
    @Override
    public BigDecimal getPrice(Material item) {
        requirePrimaryThread("getPrice");
        ItemDef definition = requireActiveItem(item);
        return BigDecimal.valueOf(Money.round(engine.midPrice(definition.id)))
            .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Devre disi urun ({@code aktif: false}) icin sonuc basarisiz olur ve
     * {@code resultCode} {@code DEVRE_DISI} doner; MarketService ayni choke
     * point'te reddeder.
     */
    @Override
    public TradeResultDto trade(Player player,
                                Material item,
                                int amount,
                                TradeSide side) {
        requirePrimaryThread("trade");
        String itemId = item == null ? null : item.name();
        if (itemId == null || !items.containsKey(itemId)) {
            return new TradeResultDto(false, "BILINMEYEN_ITEM", 0.0);
        }

        MarketResult result = marketService.trade(player, itemId, amount, side);
        boolean success = result.outcome == TradeOutcome.OK;
        double total = result.quote == null ? 0.0 : result.quote.totalPrice;
        return new TradeResultDto(success, outcomeName(result), total);
    }

    /**
     * @throws IllegalStateException urun devre disi ({@code aktif: false})
     *     ise; getQuote/trade ile ayni choke point.
     */
    @Override
    public List<double[]> getPriceHistory(Material item, int days) {
        ItemDef definition = requireActiveItem(item);
        if (days <= 0) {
            return Collections.emptyList();
        }

        LocalDate today = currentDay();

        List<double[]> history = new ArrayList<double[]>();
        for (int offset = days - 1; offset >= 0; offset--) {
            LocalDate day = today.minusDays(offset);
            double mid = snapshotRepo.lastBefore(
                definition.id,
                day.plusDays(1L).toString()
            );
            if (mid >= 0.0) {
                history.add(new double[] {history.size(), mid});
            }
        }
        return history;
    }

    /**
     * @throws IllegalStateException urun devre disi ({@code aktif: false})
     *     ise; getQuote/trade ile ayni choke point.
     */
    @Override
    public double getDailyVolume(Material item) {
        ItemDef definition = requireActiveItem(item);
        long sinceMs = currentDay()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli();
        return txLogRepo.dailyVolumeForItem(definition.id, sinceMs);
    }

    private void requirePrimaryThread(String method) {
        if (!primaryThreadGuard.isPrimaryThread()) {
            throw new IllegalStateException(
                "MCPiyasa API " + method
                    + "() yalniz main thread'den cagrilabilir");
        }
    }

    private ItemDef requireItem(Material material) {
        String itemId = material == null ? null : material.name();
        ItemDef definition = itemId == null ? null : items.get(itemId);
        if (definition == null) {
            throw new IllegalArgumentException("Bilinmeyen item: " + itemId);
        }
        return definition;
    }

    /**
     * KUCUK: devre disi ({@code aktif: false}) urun salt-okunur API
     * yuzeylerinde de markette-degil gibi ele alinir; getQuote/trade zaten
     * ayni sekilde davraniyordu.
     */
    private ItemDef requireActiveItem(Material material) {
        ItemDef definition = requireItem(material);
        if (!definition.active) {
            throw new IllegalStateException("urun devre disi");
        }
        return definition;
    }

    private LocalDate currentDay() {
        String dayKey = clock.dayKey();
        try {
            return LocalDate.parse(dayKey);
        } catch (DateTimeException e) {
            throw new IllegalStateException(
                "Gecersiz piyasa gun anahtari: " + dayKey, e);
        }
    }

    private static String outcomeName(MarketResult result) {
        if (result.outcome != null) {
            return result.outcome.name();
        }
        if ("market.bakimda".equals(result.messageKey)) {
            return "BAKIMDA";
        }
        if ("islem.gecersiz-adet".equals(result.messageKey)) {
            return "GECERSIZ_ADET";
        }
        if ("islem.iptal-edildi".equals(result.messageKey)) {
            return "IPTAL_EDILDI";
        }
        if ("islem.alis-kapali".equals(result.messageKey)) {
            return "ALIS_KAPALI";
        }
        if ("islem.satis-kapali".equals(result.messageKey)) {
            return "SATIS_KAPALI";
        }
        if ("islem.urun-devre-disi".equals(result.messageKey)) {
            return "DEVRE_DISI";
        }
        return "HATA";
    }

    interface PrimaryThreadGuard {
        boolean isPrimaryThread();
    }

    private enum BukkitPrimaryThreadGuard implements PrimaryThreadGuard {
        INSTANCE;

        @Override
        public boolean isPrimaryThread() {
            return Bukkit.getServer() == null || Bukkit.isPrimaryThread();
        }
    }
}
