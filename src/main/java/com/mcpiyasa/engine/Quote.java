package com.mcpiyasa.engine;

/** Bir islemin hesaplanmis, commit edilene kadar durumu degistirmeyen fiyat teklifi. */
public final class Quote {
    public final String itemId;
    public final TradeSide side;
    public final int amount;
    public final double totalPrice;
    public final double unitAvg;
    public final double stockDelta;

    public Quote(String itemId, TradeSide side, int amount, double totalPrice,
                 double unitAvg, double stockDelta) {
        this.itemId = itemId;
        this.side = side;
        this.amount = amount;
        this.totalPrice = Money.round(totalPrice);
        this.unitAvg = Money.round(unitAvg);
        this.stockDelta = stockDelta;
    }
}
