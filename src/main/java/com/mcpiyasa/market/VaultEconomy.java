package com.mcpiyasa.market;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;

import java.util.UUID;

/** Vault Economy saglayicisini cekirdek islem koprusune uyarlar. */
public final class VaultEconomy implements EconomyBridge {
    private final Economy economy;

    public VaultEconomy(Economy economy) {
        if (economy == null) {
            throw new IllegalArgumentException("economy null olamaz");
        }
        this.economy = economy;
    }

    @Override
    public boolean has(UUID player, double amount) {
        return economy.has(Bukkit.getOfflinePlayer(player), amount);
    }

    @Override
    public boolean withdraw(UUID player, double amount) {
        EconomyResponse response = economy.withdrawPlayer(
            Bukkit.getOfflinePlayer(player),
            amount
        );
        return response != null && response.transactionSuccess();
    }

    @Override
    public boolean deposit(UUID player, double amount) {
        EconomyResponse response = economy.depositPlayer(
            Bukkit.getOfflinePlayer(player),
            amount
        );
        return response != null && response.transactionSuccess();
    }
}
