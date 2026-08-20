package com.mcpiyasa.access;

/** Komut miktarlarini yan etki olmadan dogrular. */
public final class AmountParser {
    private AmountParser() {
    }

    public static int parse(String value, int max) {
        try {
            int amount = Integer.parseInt(value);
            return amount > 0 && amount <= max ? amount : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
