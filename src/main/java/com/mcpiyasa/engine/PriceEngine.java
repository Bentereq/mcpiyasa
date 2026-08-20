package com.mcpiyasa.engine;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PriceEngine {
    private final EngineParams params;
    private final Map<String, GroupDef> groups;
    private final Map<String, ItemDef> items;
    private final Map<String, GroupState> states;

    public PriceEngine(EngineParams params, Map<String, GroupDef> groups, Map<String, ItemDef> items) {
        this.params = params;
        this.groups = new LinkedHashMap<String, GroupDef>(groups);
        this.items = new LinkedHashMap<String, ItemDef>(items);
        this.states = new LinkedHashMap<String, GroupState>();
        for (GroupDef g : this.groups.values()) {
            states.put(g.id, new GroupState(g.id, g.baseStock, params.epsBase));
        }
    }

    public double midPrice(String itemId) {
        ItemDef it = requireItem(itemId);
        GroupDef g = groups.get(it.groupId);
        GroupState st = states.get(it.groupId);
        double raw = rawGroupMid(g, st.epsilon, st.stock) * it.weight;
        double[] b = clampBounds(itemId);
        return Math.min(b[1], Math.max(b[0], raw));
    }

    // s<=0 korunması: eğri paydası asla 0/negatif olmaz (tavan kelepçesi zaten devreye girer)
    private double rawGroupMid(GroupDef g, double epsilon, double stock) {
        double s = Math.max(stock, 1e-6);
        return g.basePrice * Math.pow(g.baseStock / s, epsilon);
    }

    public double[] clampBounds(String itemId) {
        ItemDef it = requireItem(itemId);
        GroupDef g = groups.get(it.groupId);
        double base = g.basePrice * it.weight;
        double min = it.minPrice != null ? it.minPrice.doubleValue() : base * params.bandMin;
        double max = it.maxPrice != null ? it.maxPrice.doubleValue() : base * params.bandMax;
        return new double[] { min, max };
    }

    public double impactMultiplier(TradeContext ctx) {
        double a = Math.pow(Math.max(ctx.anomalyRatio, 0.0), params.kappa);
        a = Math.min(params.anomalyMax, Math.max(params.anomalyMin, a));
        double fair = 1.0 / Math.max(1, ctx.activeSellers);
        double conc = 1.0 + params.hhiGamma * Math.max(0.0, ctx.hhi - fair);
        return a * conc;
    }

    public Quote quote(String itemId, int amount, TradeSide side, TradeContext ctx) {
        if (ctx == null)
            throw new IllegalArgumentException("TradeContext null olamaz");
        if (amount <= 0 || amount > params.maxTradeAmount)
            throw new IllegalArgumentException("Gecersiz adet: " + amount);
        ItemDef it = requireItem(itemId);
        GroupDef g = groups.get(it.groupId);
        GroupState st = states.get(it.groupId);
        double m = impactMultiplier(ctx);
        double stepImpact = it.weight * m;
        double[] b = clampBounds(itemId);
        double s = st.stock;
        long totalCents = 0L;
        try {
            for (int i = 0; i < amount; i++) {
                // BUY s->s-d gecisini yeni uctan, ters SELL ise ayni uctan
                // fiyatlar. Boylece iki yon ayni stok diliminin ayni
                // quadrature noktasini kullanir.
                if (side == TradeSide.BUY) {
                    s -= stepImpact;
                }
                double mid = rawGroupMid(g, st.epsilon, s) * it.weight;
                mid = Math.min(b[1], Math.max(b[0], mid));
                double buyUnit = mid * (1 + params.sigma / 2);
                double sellUnit = mid * (1 - params.sigma / 2);
                long stepCents = side == TradeSide.BUY
                    ? Money.buyCents(buyUnit, sellUnit)
                    : Money.sellCents(sellUnit);
                // Cent birikimi overflow-safe: asiri yuksek taban fiyat
                // (hand-edited items.yml, tavan bypass) tekil donusumde ya da
                // birikimde tassa sessiz negatif/cop toplam yerine temiz bir
                // hata firlatilir (asagida yakalanir).
                totalCents = Math.addExact(totalCents, stepCents);
                if (side == TradeSide.SELL) {
                    s += stepImpact;
                }
            }
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(
                "Fiyat teklifi hesaplanamadi (tasma): itemId=" + itemId,
                overflow);
        }
        double delta = side == TradeSide.BUY ? -stepImpact * amount : stepImpact * amount;
        double total = Money.fromCents(totalCents);
        return new Quote(itemId, side, amount, total,
                         Money.round(total / amount), delta);
    }

    public void commit(Quote q) {
        ItemDef it = requireItem(q.itemId);
        GroupState st = states.get(it.groupId);
        st.stock = Math.max(1e-6, st.stock + q.stockDelta);
    }

    public void reversionTick() {
        for (GroupState st : states.values()) {
            GroupDef g = groups.get(st.groupId);
            st.stock = st.stock + params.lambda * (g.baseStock - st.stock);
        }
    }

    public Map<String, double[]> recalibrate(Map<String, Double> emaDailyVolumeByGroup) {
        Map<String, double[]> changes = new LinkedHashMap<String, double[]>();
        for (Map.Entry<String, Double> entry : emaDailyVolumeByGroup.entrySet()) {
            Double v = entry.getValue();
            if (v == null || Double.isNaN(v)) continue;
            GroupState st = states.get(entry.getKey());
            if (st == null) continue;
            double oldEpsilon = st.epsilon;
            double newEpsilon = Calibration.epsilonFor(params, v.doubleValue());
            st.epsilon = newEpsilon;
            changes.put(entry.getKey(), new double[] { oldEpsilon, newEpsilon });
        }
        return changes;
    }

    public GroupState state(String groupId) { return states.get(groupId); }

    /** Canli grup durumlarinin reload icin degismez bir kopyasini alir. */
    public EngineSnapshot snapshot() {
        Map<String, EngineSnapshot.State> snapshotStates =
            new LinkedHashMap<String, EngineSnapshot.State>();
        for (Map.Entry<String, GroupState> entry : states.entrySet()) {
            GroupState state = entry.getValue();
            snapshotStates.put(
                entry.getKey(),
                new EngineSnapshot.State(state.stock, state.epsilon));
        }
        return new EngineSnapshot(snapshotStates);
    }

    /** Yalniz bu engine'de de bulunan gruplarin canli durumunu devralir. */
    public void restoreSnapshot(EngineSnapshot snapshot) {
        restoreSnapshot(snapshot, 1.0);
    }

    /**
     * Yalniz bu engine'de de bulunan gruplarin canli durumunu devralir;
     * stok her grupta {@code stockScaleFactor} ile carpilarak yeniden
     * olceklenir.
     *
     * <p>{@code hassasiyet} degisince baseStock de degisir
     * ({@code configBaseStock / hassasiyet}); stok
     * {@code hassasiyet_eski / hassasiyet_yeni} ile olceklenince
     * {@code baseStock/stock} orani (dolayisiyla fiyat) korunur. Faktor
     * sonlu ve pozitif degilse (cagiran hatasi/bozuk deger) olcekleme
     * uygulanmaz (1.0 gibi davranir); tek bir grubun olceklenmis stogu
     * sonlu&pozitif cikmazsa da o grup icin olceksiz deger kullanilir.
     */
    public void restoreSnapshot(EngineSnapshot snapshot, double stockScaleFactor) {
        if (snapshot == null) {
            throw new IllegalArgumentException("EngineSnapshot null olamaz");
        }
        double scale = Double.isFinite(stockScaleFactor) && stockScaleFactor > 0.0
            ? stockScaleFactor : 1.0;
        for (Map.Entry<String, EngineSnapshot.State> entry
                : snapshot.states().entrySet()) {
            GroupState state = states.get(entry.getKey());
            if (state != null) {
                EngineSnapshot.State saved = entry.getValue();
                double rescaledStock = saved.stock * scale;
                state.stock = Double.isFinite(rescaledStock) && rescaledStock > 0.0
                    ? rescaledStock : saved.stock;
                state.epsilon = saved.epsilon;
            }
        }
    }

    public void restoreState(String groupId, double stock, double epsilon) {
        GroupState st = states.get(groupId);
        if (st != null) { st.stock = stock; st.epsilon = epsilon; }
    }

    private ItemDef requireItem(String itemId) {
        ItemDef it = items.get(itemId);
        if (it == null) throw new IllegalArgumentException("Bilinmeyen item: " + itemId);
        return it;
    }
}
