package com.mcpiyasa.api;

import com.mcpiyasa.api.events.MarketPreTradeEvent;
import com.mcpiyasa.engine.Quote;
import com.mcpiyasa.market.TradePreHook;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** MarketService on-islem kancasini iptal edilebilir Bukkit event'ine baglar. */
public final class EventTradePreHook implements TradePreHook {
    @Override
    public boolean allow(Player player, Quote quote) {
        MarketPreTradeEvent event = new MarketPreTradeEvent(
            player,
            quote.itemId,
            quote.amount,
            quote.side,
            quote.totalPrice
        );
        if (Bukkit.getServer() != null) {
            Bukkit.getPluginManager().callEvent(event);
        }
        return !event.isCancelled();
    }
}
