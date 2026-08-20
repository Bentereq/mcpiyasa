package com.mcpiyasa.market;

import java.util.UUID;

/** Storage'a aktarilan degismez islem kaydi. */
public final class TradeRecord {
    public final long timeMs;
    public final UUID player;
    public final String itemId;
    public final String groupId;
    public final int amount;
    public final String side;
    public final double totalPrice;
    public final double weight;
    public final String result;

    public TradeRecord(
        long timeMs,
        UUID player,
        String itemId,
        String groupId,
        int amount,
        String side,
        double totalPrice,
        double weight,
        String result
    ) {
        this.timeMs = timeMs;
        this.player = player;
        this.itemId = itemId;
        this.groupId = groupId;
        this.amount = amount;
        this.side = side;
        this.totalPrice = totalPrice;
        this.weight = weight;
        this.result = result;
    }
}
