package com.mcpiyasa.api.events;

import com.mcpiyasa.engine.TradeSide;
import com.mcpiyasa.market.TradeOutcome;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Basariyla tamamlanan bir ticaretten sonra yayinlanan event. */
public final class MarketTradeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String itemId;
    private final int amount;
    private final TradeSide side;
    private final double totalPrice;
    private final TradeOutcome outcome;

    public MarketTradeEvent(Player player,
                            String itemId,
                            int amount,
                            TradeSide side,
                            double totalPrice,
                            TradeOutcome outcome) {
        this.player = player;
        this.itemId = itemId;
        this.amount = amount;
        this.side = side;
        this.totalPrice = totalPrice;
        this.outcome = outcome;
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

    public TradeOutcome getOutcome() {
        return outcome;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
