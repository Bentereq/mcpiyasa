package com.mcpiyasa.config;

import com.mcpiyasa.compat.Materials;
import com.mcpiyasa.engine.GroupDef;
import com.mcpiyasa.engine.ItemDef;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * items.yml dosyasini surum uyumlulugunu koruyarak yukler.
 * Gecersiz sayisal veya bant degeri iceren item/gruplar atlanir.
 */
public final class ItemsLoader {
    /** Ana menu, Movers acikken 16 kategori slotu sunar. */
    public static final int MAX_CATEGORIES = 16;

    private ItemsLoader() {
    }

    public static ParsedItems load(Configuration cfg, double varsayilanTabanStok) {
        return load(cfg, varsayilanTabanStok, 1.0);
    }

    /**
     * @param hassasiyet fiyatin hacme tepki hizini ayarlayan kuresel carpan;
     *     her grubun taban stogunu {@code baseStock / hassasiyet} olarak
     *     olceklendirir. Baslangic fiyati (s = s0) degismez, yalniz tepki hizi
     *     degisir. Butun gruplar ayni oranda olceklendigi icin craft-cevrim
     *     zarar degismezleri korunur (arbitraj acilmaz).
     */
    public static ParsedItems load(Configuration cfg, double varsayilanTabanStok,
                                   double hassasiyet) {
        return load(cfg, varsayilanTabanStok, hassasiyet, Double.MAX_VALUE);
    }

    /**
     * @param maxTabanFiyat bir grup/bagimsiz item taban fiyatinin asamayacagi
     *     ust sinir (long-cent birikiminde tasmayi engeller). Asan
     *     item/grup, diger sayisal kapilarla ayni sekilde fail-closed atlanir.
     */
    public static ParsedItems load(Configuration cfg, double varsayilanTabanStok,
                                   double hassasiyet, double maxTabanFiyat) {
        List<String> diagnostics = new ArrayList<String>();
        List<String> notices = new ArrayList<String>();
        List<CategoryDef> categories = loadCategories(cfg, diagnostics);
        Map<String, List<String>> categoryItems = createCategoryItems(categories);
        List<String> skipped = new ArrayList<String>();
        Set<String> configuredGroupIds = configuredGroupIds(cfg);
        Map<String, GroupDef> configuredGroups = loadConfiguredGroups(
            cfg, varsayilanTabanStok, skipped, hassasiyet, maxTabanFiyat);

        Map<String, ItemDef> items = new LinkedHashMap<String, ItemDef>();
        Map<String, GroupDef> implicitGroups = new LinkedHashMap<String, GroupDef>();
        Map<String, String> itemCategory = new LinkedHashMap<String, String>();
        Set<String> usedConfiguredGroups = new LinkedHashSet<String>();

        ConfigurationSection itemSection = cfg.getConfigurationSection("itemler");
        if (itemSection != null) {
            Map<String, String> rawByCanonical = new LinkedHashMap<String, String>();
            for (String rawItemId : itemSection.getKeys(false)) {
                org.bukkit.Material resolved = Materials.resolve(rawItemId);
                if (resolved == null) {
                    skipped.add(rawItemId);
                    continue;
                }
                // Item olmayan ya da yiginlanamayan materyal (AIR/WATER ...)
                // teslimat yolunu guvenli moda dusurur; yapilandirma
                // kaynakli DoS'u burada kesiyoruz.
                if (!Materials.isMarketable(resolved)) {
                    skipped.add(rawItemId);
                    diagnostics.add(
                        "itemler." + rawItemId
                            + " bir item degil; market anahtari olamaz");
                    continue;
                }
                String itemId = resolved.name();
                String previousRaw = rawByCanonical.put(itemId, rawItemId);
                if (previousRaw != null) {
                    throw new IllegalArgumentException(
                        "Ayni Material birden cok item anahtariyla tanimlandi: "
                            + previousRaw + ", " + rawItemId
                            + " -> " + itemId);
                }
                ConfigurationSection itemCfg =
                    itemSection.getConfigurationSection(rawItemId);
                if (itemCfg == null) {
                    skipped.add(rawItemId);
                    continue;
                }
                String categoryId = itemCfg.getString("kategori");
                if (categoryId != null && !categoryItems.containsKey(categoryId)) {
                    skipped.add(rawItemId);
                    diagnostics.add(
                        "itemler." + rawItemId + ".kategori bilinmiyor: "
                            + categoryId);
                    continue;
                }
                if (itemCfg.contains("agirlik")
                        && !isPositiveFiniteNumber(itemCfg, "agirlik")) {
                    skipped.add(rawItemId);
                    continue;
                }
                if ((itemCfg.contains("min-fiyat")
                        && !isPositiveFiniteNumber(itemCfg, "min-fiyat"))
                        || (itemCfg.contains("max-fiyat")
                        && !isPositiveFiniteNumber(itemCfg, "max-fiyat"))) {
                    skipped.add(rawItemId);
                    continue;
                }
                if ((itemCfg.contains("alis-acik")
                        && !isBoolean(itemCfg, "alis-acik"))
                        || (itemCfg.contains("satis-acik")
                        && !isBoolean(itemCfg, "satis-acik"))
                        || (itemCfg.contains("aktif")
                        && !isBoolean(itemCfg, "aktif"))) {
                    skipped.add(rawItemId);
                    continue;
                }
                Double minPrice = positiveOptionalDouble(
                    itemCfg, "min-fiyat");
                Double maxPrice = positiveOptionalDouble(
                    itemCfg, "max-fiyat");
                if (minPrice != null && maxPrice != null
                        && !(minPrice.doubleValue() < maxPrice.doubleValue())) {
                    skipped.add(rawItemId);
                    continue;
                }

                String groupId = itemCfg.getString("grup");
                if (groupId == null) {
                    if (configuredGroupIds.contains(itemId)
                            || !isPositiveFiniteWithinCeiling(
                                itemCfg, "taban-fiyat", maxTabanFiyat)
                            || (itemCfg.contains("taban-stok")
                                && !isPositiveFiniteNumber(
                                    itemCfg, "taban-stok"))
                            || (!itemCfg.contains("taban-stok")
                                && !isPositiveFinite(
                                    varsayilanTabanStok))) {
                        skipped.add(rawItemId);
                        continue;
                    }
                    groupId = itemId;
                    double basePrice = itemCfg.getDouble("taban-fiyat");
                    double baseStock = itemCfg.contains("taban-stok")
                        ? itemCfg.getDouble("taban-stok") : varsayilanTabanStok;
                    implicitGroups.put(groupId, new GroupDef(
                        groupId, basePrice, baseStock / hassasiyet));
                } else if (!configuredGroups.containsKey(groupId)) {
                    skipped.add(rawItemId);
                    continue;
                } else {
                    usedConfiguredGroups.add(groupId);
                }

                double weight = itemCfg.contains("agirlik") ? itemCfg.getDouble("agirlik") : 1.0;
                boolean alisAcik = itemCfg.getBoolean("alis-acik", true);
                boolean satisAcik = itemCfg.getBoolean("satis-acik", true);
                boolean aktif = itemCfg.getBoolean("aktif", true);
                items.put(itemId, new ItemDef(
                    itemId, groupId, weight, minPrice, maxPrice,
                    alisAcik, satisAcik, aktif));
                String metaNotice = metaNotice(resolved);
                if (metaNotice != null) {
                    notices.add(metaNotice);
                }

                if (categoryId != null) {
                    itemCategory.put(itemId, categoryId);
                    List<String> categoryItemIds = categoryItems.get(categoryId);
                    categoryItemIds.add(itemId);
                }
            }
        }

        Map<String, GroupDef> groups = new LinkedHashMap<String, GroupDef>();
        for (Map.Entry<String, GroupDef> entry : configuredGroups.entrySet()) {
            if (usedConfiguredGroups.contains(entry.getKey())) {
                groups.put(entry.getKey(), entry.getValue());
            }
        }
        groups.putAll(implicitGroups);

        return new ParsedItems(
            groups, items, categories, itemCategory, categoryItems, skipped,
            diagnostics, notices);
    }

    /**
     * Hasarli veya meta tasiyan materyaller markete eklenebilir; bu sahibin
     * tercihidir. Yalniz davranis sessiz kalmasin diye bir INFO satiri uretir.
     */
    static String metaNotice(org.bukkit.Material material) {
        boolean damageable = Materials.isDamageable(material);
        boolean metaBearing = Materials.defaultStackHasMeta(material);
        if (!damageable && !metaBearing) {
            return null;
        }
        return material.name()
            + ": " + (damageable ? "hasarli" : "meta")
            + " materyal - yalnizca birebir ayni (isSimilar) yiginlar"
            + " market gorur";
    }

    private static List<CategoryDef> loadCategories(
            Configuration cfg, List<String> diagnostics) {
        List<CategoryDef> categories = new ArrayList<CategoryDef>();
        ConfigurationSection section = cfg.getConfigurationSection("kategoriler");
        if (section == null) {
            return categories;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection categoryCfg = section.getConfigurationSection(id);
            if (categoryCfg == null) {
                diagnostics.add("kategoriler." + id + " bir bolum olmali");
                continue;
            }
            String icon = categoryCfg.getString("ikon");
            org.bukkit.Material material = Materials.resolve(icon);
            Object rawOrder = categoryCfg.get("sira");
            if (!Materials.isMarketable(material)) {
                diagnostics.add(
                    "kategoriler." + id + ".ikon gecersiz: " + icon);
                continue;
            }
            if (!(rawOrder instanceof Number)
                    || ((Number) rawOrder).doubleValue()
                        != Math.rint(((Number) rawOrder).doubleValue())
                    || ((Number) rawOrder).intValue() < 1) {
                diagnostics.add(
                    "kategoriler." + id + ".sira pozitif tam sayi olmali");
                continue;
            }
            categories.add(new CategoryDef(
                id, material.name(), ((Number) rawOrder).intValue()));
        }
        Collections.sort(categories, new Comparator<CategoryDef>() {
            @Override
            public int compare(CategoryDef left, CategoryDef right) {
                return Integer.compare(left.sira, right.sira);
            }
        });
        if (categories.size() > MAX_CATEGORIES) {
            diagnostics.add(
                "Ana menu en fazla " + MAX_CATEGORIES
                    + " gecerli kategori destekler; bulunan: "
                    + categories.size());
            return new ArrayList<CategoryDef>(
                categories.subList(0, MAX_CATEGORIES));
        }
        return categories;
    }

    private static Map<String, List<String>> createCategoryItems(List<CategoryDef> categories) {
        Map<String, List<String>> categoryItems = new LinkedHashMap<String, List<String>>();
        for (CategoryDef category : categories) {
            categoryItems.put(category.id, new ArrayList<String>());
        }
        return categoryItems;
    }

    private static Map<String, GroupDef> loadConfiguredGroups(
            Configuration cfg, double varsayilanTabanStok, List<String> skipped,
            double hassasiyet, double maxTabanFiyat) {
        Map<String, GroupDef> groups = new LinkedHashMap<String, GroupDef>();
        ConfigurationSection section = cfg.getConfigurationSection("gruplar");
        if (section == null) {
            return groups;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection groupCfg = section.getConfigurationSection(id);
            if (groupCfg == null
                    || !isPositiveFiniteWithinCeiling(
                        groupCfg, "taban-fiyat", maxTabanFiyat)
                    || (groupCfg.contains("taban-stok")
                        && !isPositiveFiniteNumber(groupCfg, "taban-stok"))
                    || (!groupCfg.contains("taban-stok")
                        && !isPositiveFinite(varsayilanTabanStok))) {
                skipped.add(id);
                continue;
            }
            double baseStock = groupCfg.contains("taban-stok")
                ? groupCfg.getDouble("taban-stok") : varsayilanTabanStok;
            groups.put(id, new GroupDef(
                id, groupCfg.getDouble("taban-fiyat"), baseStock / hassasiyet));
        }
        return groups;
    }

    private static Set<String> configuredGroupIds(Configuration cfg) {
        ConfigurationSection section = cfg.getConfigurationSection("gruplar");
        if (section == null) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<String>(section.getKeys(false));
    }

    private static boolean isNumber(ConfigurationSection section, String key) {
        return section.get(key) instanceof Number;
    }

    private static boolean isBoolean(ConfigurationSection section, String key) {
        return section.get(key) instanceof Boolean;
    }

    private static boolean isPositiveFiniteNumber(
            ConfigurationSection section, String key) {
        return isNumber(section, key)
            && isPositiveFinite(section.getDouble(key));
    }

    private static boolean isPositiveFinite(double value) {
        return Double.isFinite(value) && value > 0.0;
    }

    /**
     * Taban fiyat sonlu, pozitif VE {@code maxTabanFiyat} tavanini
     * asmiyor mu. Asan item/grup diger sayisal kapilarla ayni sekilde
     * atlanir (long-cent birikiminde tasmayi engellemek icin).
     */
    private static boolean isPositiveFiniteWithinCeiling(
            ConfigurationSection section, String key, double maxTabanFiyat) {
        return isPositiveFiniteNumber(section, key)
            && section.getDouble(key) <= maxTabanFiyat;
    }

    private static Double positiveOptionalDouble(
            ConfigurationSection section, String key) {
        return section.contains(key)
            ? Double.valueOf(section.getDouble(key)) : null;
    }
}
