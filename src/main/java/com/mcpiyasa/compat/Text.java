package com.mcpiyasa.compat;

import org.bukkit.ChatColor;

public final class Text {
    private Text() {
    }

    public static String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }
}
