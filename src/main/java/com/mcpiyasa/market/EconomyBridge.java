package com.mcpiyasa.market;

import java.util.UUID;

public interface EconomyBridge {
    boolean has(UUID player, double amount);

    /**
     * Tümü-veya-hiç: işlem ya tamamen uygulanır ya hiç uygulanmaz; kısmi uygulama YASAKTIR (Vault withdrawPlayer sonucunun başarı durumu esas alınır).
     */
    boolean withdraw(UUID player, double amount);

    /**
     * Tümü-veya-hiç: işlem ya tamamen uygulanır ya hiç uygulanmaz; kısmi uygulama YASAKTIR (Vault depositPlayer sonucunun başarı durumu esas alınır).
     */
    boolean deposit(UUID player, double amount);
}
