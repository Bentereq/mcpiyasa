package com.mcpiyasa.market;

import java.util.UUID;

public interface InventoryBridge {
    int count(UUID player, String itemId);

    /**
     * Tümü-veya-hiç: işlem ya tamamen uygulanır ya hiç uygulanmaz; kısmi uygulama YASAKTIR (Bukkit Inventory#removeItem kısmi kaldırır — adaptör bunu telafi etmek zorundadır).
     */
    boolean remove(UUID player, String itemId, int amount);

    /**
     * Kapasite hesabı, item türünün {@code maxStackSize} değerine uymalıdır.
     */
    int freeCapacity(UUID player, String itemId);

    /**
     * Tümü-veya-hiç: işlem ya tamamen uygulanır ya hiç uygulanmaz; kısmi uygulama YASAKTIR (Bukkit Inventory#addItem kısmi doldurur — adaptör bunu telafi etmek zorundadır).
     */
    boolean add(UUID player, String itemId, int amount);

    /**
     * Oyuncunun envanterinde ayni materyalden fakat {@code isSimilar}
     * esiginden gecmeyen (yeniden adlandirilmis/buyulu/hasarli) bir yigin
     * varsa true. Yalniz kullaniciya dogru mesaji secmek icindir; odeme
     * kararina girmez.
     */
    default boolean hasModifiedStack(UUID player, String itemId) {
        return false;
    }
}
