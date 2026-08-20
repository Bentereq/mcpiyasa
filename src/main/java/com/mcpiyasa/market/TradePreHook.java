package com.mcpiyasa.market;

import com.mcpiyasa.engine.Quote;
import org.bukkit.entity.Player;

/** Dış dinleyicinin bir kotasyonu transfer başlamadan önce iptal edebildiği kanca. */
public interface TradePreHook {
    TradePreHook ALLOW_ALL = new TradePreHook() {
        @Override
        public boolean allow(Player player, Quote quote) {
            return true;
        }
    };

    boolean allow(Player player, Quote quote);
}
