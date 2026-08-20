package com.mcpiyasa.api;

import com.mcpiyasa.engine.TradeSide;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Plugin omru boyunca kimligi degismeyen, atomik delegate'li API facade'i. */
public final class ApiFacade implements MCPiyasaAPI {
    private final AtomicReference<MCPiyasaAPI> delegate =
        new AtomicReference<MCPiyasaAPI>(UnavailableApi.INSTANCE);

    public void swap(MCPiyasaAPI next) {
        if (next == null) {
            throw new IllegalArgumentException("API delegate null olamaz");
        }
        delegate.set(next);
    }

    public void unavailable() {
        delegate.set(UnavailableApi.INSTANCE);
    }

    @Override
    public PriceQuoteDto getQuote(Material item, int amount, TradeSide side) {
        return delegate.get().getQuote(item, amount, side);
    }

    @Override
    public BigDecimal getPrice(Material item) {
        return delegate.get().getPrice(item);
    }

    @Override
    public TradeResultDto trade(Player player,
                                Material item,
                                int amount,
                                TradeSide side) {
        return delegate.get().trade(player, item, amount, side);
    }

    @Override
    public List<double[]> getPriceHistory(Material item, int days) {
        return delegate.get().getPriceHistory(item, days);
    }

    @Override
    public double getDailyVolume(Material item) {
        return delegate.get().getDailyVolume(item);
    }

    private enum UnavailableApi implements MCPiyasaAPI {
        INSTANCE;

        @Override
        public PriceQuoteDto getQuote(
                Material item, int amount, TradeSide side) {
            throw unavailable();
        }

        @Override
        public BigDecimal getPrice(Material item) {
            throw unavailable();
        }

        @Override
        public TradeResultDto trade(Player player,
                                    Material item,
                                    int amount,
                                    TradeSide side) {
            return new TradeResultDto(false, "UNAVAILABLE", 0.0);
        }

        @Override
        public List<double[]> getPriceHistory(Material item, int days) {
            throw unavailable();
        }

        @Override
        public double getDailyVolume(Material item) {
            throw unavailable();
        }

        private static IllegalStateException unavailable() {
            return new IllegalStateException("MCPiyasa API UNAVAILABLE");
        }
    }
}
