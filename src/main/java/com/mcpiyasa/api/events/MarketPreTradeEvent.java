package com.mcpiyasa.api.events;

import com.mcpiyasa.engine.TradeSide;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.entity.Player;

/** Bir ticaret yurutulmeden once yayinlanan iptal edilebilir event. */
public final class MarketPreTradeEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String itemId;
    private final int amount;
    private final TradeSide side;
    private final double totalPrice;
    private boolean cancelled;

    public MarketPreTradeEvent(Player player,
                               String itemId,
                               int amount,
                               TradeSide side,
                               double totalPrice) {
        this.player = player;
        this.itemId = itemId;
        this.amount = amount;
        this.side = side;
        this.totalPrice = totalPrice;
    }

    public Player getPlayer() {
        return player;
    }

    public String getItemId() {
        return itemId;
    }

    public int getAmount() {
        return amount;
    }

    public TradeSide getSide() {
        return side;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
