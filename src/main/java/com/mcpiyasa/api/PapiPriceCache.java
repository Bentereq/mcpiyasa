package com.mcpiyasa.api;

import com.mcpiyasa.config.ParsedItems;
import com.mcpiyasa.engine.Money;
import com.mcpiyasa.engine.PriceEngine;
import com.mcpiyasa.gui.CategoryMenu;
import com.mcpiyasa.gui.Change24h;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Main thread'de uretilen, PAPI cagrilarinda salt-okunur fiyat gorunumu. */
public final class PapiPriceCache {
    private final double sigma;
    private volatile Map<String, double[]> values = Collections.emptyMap();

    public PapiPriceCache(double sigma) {
        if (Double.isNaN(sigma) || Double.isInfinite(sigma)
                || sigma < 0.0 || sigma > 1.0) {
            throw new IllegalArgumentException(
                "PAPI fiyat onbellegi sigma degeri 0 ile 1 arasinda olmalidir");
        }
        this.sigma = sigma;
    }

    /**
     * Canli engine ve snapshot deposuna yalniz bu senkron yenileme yolunda dokunur.
     * Yeni harita tamamlanmadan volatile referans degistirilmez.
     */
    public void rebuild(PriceEngine engine,
                        ParsedItems parsedItems,
                        CategoryMenu.Change24hLookup change24h) {
        if (engine == null || parsedItems == null || change24h == null) {
            throw new IllegalArgumentException(
                "PAPI fiyat onbellegi bagimliliklari null olamaz");
        }

        CategoryMenu.Change24hLookup cachedChanges =
            Change24h.memoized(change24h, parsedItems.items.keySet());
        Map<String, double[]> rebuilt = new LinkedHashMap<String, double[]>();
        for (String itemId : parsedItems.items.keySet()) {
            com.mcpiyasa.engine.ItemDef def = parsedItems.items.get(itemId);
            if (def != null && !def.active) {
                // Devre disi urunler PlaceholderAPI'de gorunmez (knows=false).
                continue;
            }
            try {
                double mid = engine.midPrice(itemId);
                double buy = Money.round(mid * (1.0 + sigma / 2.0));
                double sell = Money.round(mid * (1.0 - sigma / 2.0));
                double change = cachedChanges == null
                    ? 0.0 : cachedChanges.percent(itemId);
                rebuilt.put(itemId, new double[] {mid, buy, sell, change});
            } catch (IllegalArgumentException | IllegalStateException ignored) {
                // Tek bozuk item diger bilinen fiyatlarin yenilenmesini engellemez.
            }
        }
        values = Collections.unmodifiableMap(rebuilt);
    }

    /** Bir placeholder cozumu boyunca ayni degismez haritayi okuyan gorunum. */
    public PriceView snapshotView() {
        return new SnapshotPriceView(values);
    }

    private static final class SnapshotPriceView implements PriceView {
        private final Map<String, double[]> values;

        private SnapshotPriceView(Map<String, double[]> values) {
            this.values = values;
        }

        @Override
        public double mid(String id) {
            return values.get(id)[0];
        }

        @Override
        public double buyUnit(String id) {
            return values.get(id)[1];
        }

        @Override
        public double sellUnit(String id) {
            return values.get(id)[2];
        }

        @Override
        public double change24h(String id) {
            return values.get(id)[3];
        }

        @Override
        public boolean knows(String id) {
            return values.containsKey(id);
        }
    }
}
