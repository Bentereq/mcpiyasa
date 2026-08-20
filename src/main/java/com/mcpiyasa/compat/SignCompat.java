package com.mcpiyasa.compat;

import org.bukkit.block.Sign;

public final class SignCompat {
    private SignCompat() {
    }

    // 1.20+'ta deprecated ama mevcut ve ön yüze delege; reflection GEREKMEZ.
    public static String getLine(Sign sign, int index) {
        return sign.getLine(index);
    }

    public static void setLine(Sign sign, int index, String value) {
        sign.setLine(index, value);
    }
}
