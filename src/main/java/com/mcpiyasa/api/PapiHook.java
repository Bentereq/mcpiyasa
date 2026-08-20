package com.mcpiyasa.api;

import com.mcpiyasa.config.ParsedItems;
import com.mcpiyasa.engine.PriceEngine;
import com.mcpiyasa.gui.CategoryMenu;
import org.bukkit.plugin.Plugin;

/**
 * PlaceholderAPI siniflarini ana plugin sinifindan uzak tutan lazy kopru.
 *
 * <p>PAPI'ye dogrudan bagli {@link PapiExpansion} yalniz varlik kontrolunden
 * sonra cagrilan ic sinif yuklendiginde cozumlenir. Dis sinifin alan ve metot
 * imzalari soft-dependency olmadan guvenle yuklenebilir.</p>
 */
public final class PapiHook {
    private final PapiPriceCache priceCache;
    private final Object expansion;

    private PapiHook(PapiPriceCache priceCache, Object expansion) {
        this.priceCache = priceCache;
        this.expansion = expansion;
    }

    public static PapiHook create(Plugin plugin,
                                  double sigma,
                                  PriceEngine engine,
                                  ParsedItems parsedItems,
                                  CategoryMenu.Change24hLookup change24h) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin null olamaz");
        }
        PapiPriceCache priceCache = new PapiPriceCache(sigma);
        priceCache.rebuild(engine, parsedItems, change24h);
        return new PapiHook(
            priceCache, PapiRegistration.create(plugin, priceCache));
    }

    public boolean register() {
        return PapiRegistration.register(expansion);
    }

    public void unregister() {
        PapiRegistration.unregister(expansion);
    }

    public void refresh(PriceEngine engine,
                        ParsedItems parsedItems,
                        CategoryMenu.Change24hLookup change24h) {
        priceCache.rebuild(engine, parsedItems, change24h);
    }

    /** Yalniz PlaceholderAPI varlik kontrolunden sonra yuklenir. */
    private static final class PapiRegistration {
        private PapiRegistration() {
        }

        private static Object create(
                Plugin plugin, PapiPriceCache priceCache) {
            return new PapiExpansion(plugin, priceCache);
        }

        private static boolean register(Object expansion) {
            return ((PapiExpansion) expansion).register();
        }

        private static void unregister(Object expansion) {
            ((PapiExpansion) expansion).unregister();
        }
    }
}
