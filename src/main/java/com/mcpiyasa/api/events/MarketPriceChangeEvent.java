package com.mcpiyasa.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Basarili ticaretin orta fiyati degistirdigini bildiren event. */
public final class MarketPriceChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String itemId;
    private final double oldMid;
    private final double newMid;

    public MarketPriceChangeEvent(String itemId, double oldMid, double newMid) {
        this.itemId = itemId;
        this.oldMid = oldMid;
        this.newMid = newMid;
    }

    public String getItemId() {
        return itemId;
    }

    public double getOldMid() {
        return oldMid;
    }

    public double getNewMid() {
        return newMid;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
