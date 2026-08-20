package com.mcpiyasa.api;

import com.mcpiyasa.engine.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Bir API ticaret cagrisinin sade ve kararlı sonucu. */
public final class TradeResultDto {
    public final boolean success;
    public final String outcome;
    public final BigDecimal total;

    public TradeResultDto(boolean success, String outcome, double total) {
        this.success = success;
        this.outcome = outcome;
        this.total = BigDecimal.valueOf(Money.round(total))
            .setScale(2, RoundingMode.HALF_UP);
    }
}
