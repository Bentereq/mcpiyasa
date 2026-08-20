package com.mcpiyasa.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    private Money() {}

    public static double round(double value) {
        return BigDecimal.valueOf(value)
            .setScale(2, RoundingMode.HALF_UP)
            .doubleValue();
    }

    /**
     * Oyuncunun odedigi birim fiyati yukari yuvarlar. Spread cent
     * cozunurlugunde kaybolursa ters SELL fiyatindan en az bir cent yukarida
     * tutar.
     */
    static long buyCents(double buyUnit, double reverseSellUnit) {
        long roundedBuy = cents(buyUnit, RoundingMode.CEILING);
        long roundedSell = cents(reverseSellUnit, RoundingMode.FLOOR);
        return Math.max(roundedBuy, roundedSell + 1L);
    }

    /** Oyuncuya odenen birim fiyati asagi yuvarlar. */
    static long sellCents(double sellUnit) {
        return cents(sellUnit, RoundingMode.FLOOR);
    }

    static double fromCents(long cents) {
        return BigDecimal.valueOf(cents, 2).doubleValue();
    }

    private static long cents(double value, RoundingMode mode) {
        return BigDecimal.valueOf(value)
            .movePointRight(2)
            .setScale(0, mode)
            .longValueExact();
    }
}
