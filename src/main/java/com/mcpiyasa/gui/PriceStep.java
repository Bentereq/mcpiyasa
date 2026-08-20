package com.mcpiyasa.gui;

import com.mcpiyasa.engine.Money;

/**
 * Admin item duzenleyicisindeki tikla-fiyatla adimlari. Bukkit'ten bagimsiz
 * saf hesap: mevcut taban birim fiyata carpan ve sabit ekler, pozitif tabana
 * kirpip iki ondaliga yuvarlar. YAML yazma yolu AdminCommands'ta kalir; bu
 * sinif yalniz yeni hedef birim fiyati uretir.
 */
public enum PriceStep {
    MINUS_TEN_PERCENT(0.90, 0.0),
    MINUS_ONE(1.0, -1.0),
    PLUS_ONE(1.0, 1.0),
    PLUS_TEN_PERCENT(1.10, 0.0),
    HALVE(0.5, 0.0),
    DOUBLE(2.0, 0.0);

    /** Kirpma tabani: fiyat sifir ya da negatif olamaz. */
    public static final double MIN_PRICE = 0.01;

    private final double factor;
    private final double delta;

    PriceStep(double factor, double delta) {
        this.factor = factor;
        this.delta = delta;
    }

    /**
     * @param currentUnit mevcut taban birim fiyat
     * @return adim uygulanmis, {@link #MIN_PRICE} tabanina kirpilmis ve iki
     *     ondaliga yuvarlanmis yeni birim fiyat
     */
    public double apply(double currentUnit) {
        double base = Double.isFinite(currentUnit) && currentUnit > 0.0
            ? currentUnit : MIN_PRICE;
        double next = base * factor + delta;
        if (!Double.isFinite(next) || next < MIN_PRICE) {
            next = MIN_PRICE;
        }
        return Money.round(next);
    }
}
