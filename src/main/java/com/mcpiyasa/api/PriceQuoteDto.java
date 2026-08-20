package com.mcpiyasa.api;

import com.mcpiyasa.engine.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Ic motor tiplerini disariya sizdirmayan fiyat teklifi. */
public final class PriceQuoteDto {
    public final String itemId;
    public final int amount;
    public final BigDecimal total;
    public final BigDecimal unitAvg;

    public PriceQuoteDto(String itemId, int amount, double total, double unitAvg) {
        this.itemId = itemId;
        this.amount = amount;
        this.total = money(total);
        this.unitAvg = money(unitAvg);
    }

    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(Money.round(value))
            .setScale(2, RoundingMode.HALF_UP);
    }
}
