package com.mcpiyasa.api;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

/**
 * MCPiyasa fiyatlarini PlaceholderAPI'ye salt-okunur snapshot uzerinden acar.
 * Placeholder cagrisi canli engine veya storage katmanina dokunmaz.
 */
public final class PapiExpansion extends PlaceholderExpansion {
    private final Plugin plugin;
    private final PapiPriceCache priceCache;

    public PapiExpansion(Plugin plugin, PapiPriceCache priceCache) {
        if (plugin == null || priceCache == null) {
            throw new IllegalArgumentException(
                "PapiExpansion bagimliliklari null olamaz");
        }
        this.plugin = plugin;
        this.priceCache = priceCache;
    }

    @Override
    public String getIdentifier() {
        return "mcpiyasa";
    }

    @Override
    public String getAuthor() {
        return "tereq";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        return PlaceholderResolver.resolve(params, priceCache.snapshotView());
    }
}
