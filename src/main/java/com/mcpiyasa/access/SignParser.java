package com.mcpiyasa.access;

import com.mcpiyasa.compat.Text;
import java.util.Locale;
import org.bukkit.ChatColor;

/** Market tabelasinin baslik ve item satirlarini Bukkit sunucusu olmadan cozer. */
public final class SignParser {
    private static final String MARKET_HEADER = "[market]";

    private SignParser() {
    }

    public static String parse(String line0, String line1) {
        if (!isMarketHeader(line0) || line1 == null) {
            return null;
        }
        String itemId = line1.trim();
        return itemId.isEmpty() ? null : itemId.toUpperCase(Locale.ROOT);
    }

    static boolean isMarketHeader(String line0) {
        if (line0 == null) {
            return false;
        }
        String plain = ChatColor.stripColor(Text.color(line0));
        return MARKET_HEADER.equalsIgnoreCase(plain.trim());
    }
}
