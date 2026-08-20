package com.mcpiyasa.api;

import java.util.Locale;

/** PlaceholderAPI parametrelerini Bukkit bagimliligi olmadan cozer. */
public final class PlaceholderResolver {
    private static final String PRICE_PREFIX = "price_";
    private static final String BUY_PREFIX = "buy_";
    private static final String SELL_PREFIX = "sell_";
    private static final String CHANGE_24H_PREFIX = "change24h_";

    private PlaceholderResolver() {
    }

    public static String resolve(String params, PriceView view) {
        if (params == null || view == null) {
            return "";
        }

        String itemId;
        Value value;
        if (params.startsWith(PRICE_PREFIX)) {
            itemId = params.substring(PRICE_PREFIX.length());
            value = Value.PRICE;
        } else if (params.startsWith(BUY_PREFIX)) {
            itemId = params.substring(BUY_PREFIX.length());
            value = Value.BUY;
        } else if (params.startsWith(SELL_PREFIX)) {
            itemId = params.substring(SELL_PREFIX.length());
            value = Value.SELL;
        } else if (params.startsWith(CHANGE_24H_PREFIX)) {
            itemId = params.substring(CHANGE_24H_PREFIX.length());
            value = Value.CHANGE_24H;
        } else {
            return "";
        }

        if (!view.knows(itemId)) {
            return "";
        }
        if (value == Value.PRICE) {
            return money(view.mid(itemId));
        }
        if (value == Value.BUY) {
            return money(view.buyUnit(itemId));
        }
        if (value == Value.SELL) {
            return money(view.sellUnit(itemId));
        }
        double change = view.change24h(itemId);
        return String.format(
            Locale.ROOT, "%+.1f", change == 0.0 ? 0.0 : change);
    }

    private static String money(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private enum Value {
        PRICE,
        BUY,
        SELL,
        CHANGE_24H
    }
}
