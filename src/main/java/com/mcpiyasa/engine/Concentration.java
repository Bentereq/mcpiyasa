package com.mcpiyasa.engine;

import java.util.LinkedHashMap;
import java.util.Map;

/** Gunluk satici hacimlerinden Herfindahl-Hirschman yogunlasmasini hesaplar. */
public final class Concentration {
    private final Map<String, Double> volumes = new LinkedHashMap<String, Double>();

    /**
     * Oyuncunun gunluk satis hacmine ekler.
     * Null oyuncu kimligi ile sonlu olmayan, sifir ve negatif hacimler durumu
     * degistirmeden yok sayilir.
     */
    public void record(String playerId, double volume) {
        if (playerId == null || !(volume > 0.0) || !Double.isFinite(volume)) {
            return;
        }
        Double current = volumes.get(playerId);
        volumes.put(playerId, current == null ? volume : current.doubleValue() + volume);
    }

    /** Hacim paylarinin kareleri toplamini dondurur. */
    public double hhi() {
        if (volumes.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (Double volume : volumes.values()) {
            total += volume.doubleValue();
        }
        if (total <= 0.0) {
            return 0.0;
        }
        double result = 0.0;
        for (Double volume : volumes.values()) {
            double share = volume.doubleValue() / total;
            result += share * share;
        }
        return result;
    }

    /** En az bir kabul edilerek aktif farkli satici sayisini dondurur. */
    public int activeSellers() {
        return Math.max(1, volumes.size());
    }

    /** Verilen satis eklenseydi olusacak HHI'yi durumu degistirmeden hesaplar. */
    public double projectedHhi(String playerId, double volume) {
        if (playerId == null || !(volume > 0.0) || !Double.isFinite(volume)) {
            return hhi();
        }
        double total = volume;
        for (Double current : volumes.values()) {
            total += current.doubleValue();
        }
        double result = 0.0;
        boolean existingSeller = false;
        for (Map.Entry<String, Double> entry : volumes.entrySet()) {
            double projected = entry.getValue().doubleValue();
            if (playerId.equals(entry.getKey())) {
                projected += volume;
                existingSeller = true;
            }
            double share = projected / total;
            result += share * share;
        }
        if (!existingSeller) {
            double share = volume / total;
            result += share * share;
        }
        return result;
    }

    /** Verilen satis eklenseydi olusacak satici sayisini mutasyonsuz dondurur. */
    public int projectedActiveSellers(String playerId, double volume) {
        if (playerId == null || !(volume > 0.0) || !Double.isFinite(volume)
                || volumes.containsKey(playerId)) {
            return activeSellers();
        }
        return Math.max(1, volumes.size() + 1);
    }

    /** Yeni gun icin tum birikmis hacimleri temizler. */
    public void resetDay() {
        volumes.clear();
    }

    /** Kalicilik katmani icin canli olmayan sirali bir kopya dondurur. */
    public Map<String, Double> entries() {
        return new LinkedHashMap<String, Double>(volumes);
    }

    /**
     * Kalici durumdan hacimleri geri yukler.
     * Null girdi bos durum olarak kabul edilir.
     * Null oyuncu kimlikleri ile null, sonlu olmayan, sifir veya negatif
     * hacimler kalici durumu zehirlememeleri icin filtrelenir.
     */
    public void restore(Map<String, Double> entries) {
        volumes.clear();
        if (entries != null) {
            for (Map.Entry<String, Double> entry : entries.entrySet()) {
                String playerId = entry.getKey();
                Double volume = entry.getValue();
                if (playerId == null || volume == null
                        || !(volume.doubleValue() > 0.0)
                        || !Double.isFinite(volume.doubleValue())) {
                    continue;
                }
                volumes.put(playerId, volume);
            }
        }
    }
}
