package com.mcpiyasa.gui;

import com.mcpiyasa.engine.Money;
import com.mcpiyasa.engine.PriceEngine;
import com.mcpiyasa.market.Clock;
import com.mcpiyasa.storage.SnapshotRepo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Yirmi dort saatlik fiyat degisimini hesaplar ve depodan okur. */
public final class Change24h implements CategoryMenu.Change24hLookup {
    private final PriceEngine engine;
    private final SnapshotRepo snapshotRepo;
    private final Clock clock;

    public Change24h(PriceEngine engine, SnapshotRepo snapshotRepo, Clock clock) {
        if (engine == null || snapshotRepo == null || clock == null) {
            throw new IllegalArgumentException("Change24h bagimliliklari null olamaz");
        }
        this.engine = engine;
        this.snapshotRepo = snapshotRepo;
        this.clock = clock;
    }

    @Override
    public double percent(String itemId) {
        try {
            double snapshotMid = snapshotRepo.lastBefore(
                itemId, clock.dayKey());
            if (snapshotMid <= 0.0) {
                return 0.0;
            }
            return percent(engine.midPrice(itemId), snapshotMid);
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            return 0.0;
        }
    }

    /** Bir menu render'i boyunca her item degisimini en fazla bir kez hesaplar. */
    public static CategoryMenu.Change24hLookup memoized(
            CategoryMenu.Change24hLookup lookup, Iterable<String> itemIds) {
        if (lookup == null) {
            return null;
        }
        if (lookup instanceof Change24h) {
            return ((Change24h) lookup).memoized(itemIds);
        }

        Map<String, Double> changes = new LinkedHashMap<String, Double>();
        if (itemIds != null) {
            for (String itemId : itemIds) {
                if (itemId == null || changes.containsKey(itemId)) {
                    continue;
                }
                changes.put(itemId, guardedPercent(lookup, itemId));
            }
        }
        return new MapLookup(changes);
    }

    private CategoryMenu.Change24hLookup memoized(Iterable<String> itemIds) {
        Map<String, Double> snapshots;
        try {
            snapshots = snapshotRepo.lastBeforeAll(clock.dayKey());
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            snapshots = Collections.emptyMap();
        }

        Map<String, Double> changes = new LinkedHashMap<String, Double>();
        if (itemIds != null) {
            for (String itemId : itemIds) {
                if (itemId == null || changes.containsKey(itemId)) {
                    continue;
                }
                Double snapshot = snapshots.get(itemId);
                double snapshotMid = snapshot == null ? -1.0 : snapshot;
                try {
                    changes.put(
                        itemId, percent(engine.midPrice(itemId), snapshotMid));
                } catch (IllegalArgumentException | IllegalStateException ignored) {
                    changes.put(itemId, 0.0);
                }
            }
        }
        return new MapLookup(changes);
    }

    private static double guardedPercent(
            CategoryMenu.Change24hLookup lookup, String itemId) {
        try {
            return lookup.percent(itemId);
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            return 0.0;
        }
    }

    public static double percent(double currentMid, double snapshotMid) {
        if (snapshotMid <= 0.0) {
            return 0.0;
        }
        return Money.round((currentMid - snapshotMid) / snapshotMid * 100.0);
    }

    public static String arrow(double percent) {
        if (percent > 0.005) {
            return "▲";
        }
        if (percent < -0.005) {
            return "▼";
        }
        return "=";
    }

    private static final class MapLookup
            implements CategoryMenu.Change24hLookup {
        private final Map<String, Double> changes;

        private MapLookup(Map<String, Double> changes) {
            this.changes = Collections.unmodifiableMap(
                new LinkedHashMap<String, Double>(changes));
        }

        @Override
        public double percent(String itemId) {
            Double change = changes.get(itemId);
            return change == null ? 0.0 : change;
        }
    }
}
