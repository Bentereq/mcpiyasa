package com.mcpiyasa.config;

import com.mcpiyasa.engine.GroupDef;
import com.mcpiyasa.engine.ItemDef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** items.yml yuklemesinin sirali ve tekrar uretilebilir sonucu. */
public final class ParsedItems {
    public final Map<String, GroupDef> groups;
    public final Map<String, ItemDef> items;
    public final List<CategoryDef> categories;
    public final Map<String, String> itemCategory;
    public final Map<String, List<String>> categoryItems;
    public final List<String> skipped;
    public final List<String> diagnostics;
    /**
     * Yuklemeyi engellemeyen, yalniz operatore aciklama olan INFO satirlari.
     * {@code diagnostics}'ten farkli olarak guvenli modu tetiklemez.
     */
    public final List<String> notices;

    public ParsedItems(Map<String, GroupDef> groups,
                       Map<String, ItemDef> items,
                       List<CategoryDef> categories,
                       Map<String, String> itemCategory,
                       Map<String, List<String>> categoryItems,
                       List<String> skipped) {
        this(groups, items, categories, itemCategory, categoryItems, skipped,
            new ArrayList<String>());
    }

    public ParsedItems(Map<String, GroupDef> groups,
                       Map<String, ItemDef> items,
                       List<CategoryDef> categories,
                       Map<String, String> itemCategory,
                       Map<String, List<String>> categoryItems,
                       List<String> skipped,
                       List<String> diagnostics) {
        this(groups, items, categories, itemCategory, categoryItems, skipped,
            diagnostics, new ArrayList<String>());
    }

    public ParsedItems(Map<String, GroupDef> groups,
                       Map<String, ItemDef> items,
                       List<CategoryDef> categories,
                       Map<String, String> itemCategory,
                       Map<String, List<String>> categoryItems,
                       List<String> skipped,
                       List<String> diagnostics,
                       List<String> notices) {
        this.notices = new ArrayList<String>(notices);
        this.groups = new LinkedHashMap<String, GroupDef>(groups);
        this.items = new LinkedHashMap<String, ItemDef>(items);
        this.categories = new ArrayList<CategoryDef>(categories);
        this.itemCategory = new LinkedHashMap<String, String>(itemCategory);
        this.categoryItems = copyCategoryItems(categoryItems);
        this.skipped = new ArrayList<String>(skipped);
        this.diagnostics = new ArrayList<String>(diagnostics);
    }

    /**
     * Bir kategorinin yalniz AKTIF urunlerini, {@link #categoryItems} ile ayni
     * sirada dondurur. Oyuncu yuzeyleri (kategori menusu, tiklama yonlendirmesi,
     * item menusu sayfalamasi) bu tek suzme yolunu kullanir; boylece devre disi
     * urunler oyuncuya gorunmez ve sayfa/slot indisleri suzulmus listeye gore
     * hesaplanir. Admin yuzeyleri ham {@link #categoryItems} listesini kullanir.
     */
    public List<String> activeCategoryItems(String category) {
        List<String> all = categoryItems.get(category);
        if (all == null) {
            return new ArrayList<String>();
        }
        List<String> active = new ArrayList<String>(all.size());
        for (String itemId : all) {
            ItemDef def = items.get(itemId);
            if (def != null && def.active) {
                active.add(itemId);
            }
        }
        return active;
    }

    private static Map<String, List<String>> copyCategoryItems(
            Map<String, List<String>> categoryItems) {
        Map<String, List<String>> copy = new LinkedHashMap<String, List<String>>();
        for (Map.Entry<String, List<String>> entry : categoryItems.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<String>(entry.getValue()));
        }
        return copy;
    }
}
