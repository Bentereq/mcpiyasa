package com.mcpiyasa.gui;

import com.mcpiyasa.engine.Money;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** GUI siniflarinin Bukkit'ten bagimsiz slot ve bicimlendirme kurallari. */
public final class Icons {
    public static final int PAGE_SIZE = 45;
    public static final int MAIN_CATEGORY_START = 10;
    public static final int MOVERS_SLOT = 16;
    public static final int BACK_SLOT = 45;
    public static final int PREVIOUS_SLOT = 48;
    public static final int PAGE_INDICATOR_SLOT = 49;
    public static final int NEXT_SLOT = 50;

    private Icons() {
    }

    public static List<String> pageOf(List<String> itemIds, int page) {
        if (itemIds == null || page < 0) {
            return Collections.emptyList();
        }
        long first = (long) page * PAGE_SIZE;
        if (first >= itemIds.size()) {
            return Collections.emptyList();
        }
        int from = (int) first;
        int to = (int) Math.min((long) itemIds.size(), first + PAGE_SIZE);
        return new ArrayList<String>(itemIds.subList(from, to));
    }

    public static int pageCount(int total) {
        if (total <= 0) {
            return 1;
        }
        return ((total - 1) / PAGE_SIZE) + 1;
    }

    public static int clampPage(int page, int totalItems) {
        return Math.max(0, Math.min(page, pageCount(totalItems) - 1));
    }

    public static boolean hasPreviousPage(int page) {
        return page > 0;
    }

    public static boolean hasNextPage(int page, int totalItems) {
        return page + 1 < pageCount(totalItems);
    }

    public static int categorySlot(int categoryIndex) {
        return categorySlot(categoryIndex, false);
    }

    public static int categoryIndex(int slot) {
        return categoryIndex(slot, false);
    }

    public static int categorySlot(int categoryIndex, boolean moversEnabled) {
        if (categoryIndex < 0) {
            return -1;
        }
        int slot = MAIN_CATEGORY_START + categoryIndex;
        return moversEnabled && slot >= MOVERS_SLOT ? slot + 1 : slot;
    }

    public static int categoryIndex(int slot, boolean moversEnabled) {
        if (slot < MAIN_CATEGORY_START
                || (moversEnabled && slot == MOVERS_SLOT)) {
            return -1;
        }
        int index = slot - MAIN_CATEGORY_START;
        return moversEnabled && slot > MOVERS_SLOT ? index - 1 : index;
    }

    public static int loserSlot(int rank) {
        if (rank < 0 || rank >= 8) {
            return -1;
        }
        return 45 + rank + (rank >= 4 ? 1 : 0);
    }

    public static int loserRank(int slot) {
        if (slot >= 45 && slot <= 48) {
            return slot - 45;
        }
        if (slot >= 50 && slot <= 53) {
            return slot - 46;
        }
        return -1;
    }

    public static boolean isCategoryItemSlot(int slot) {
        return slot >= 0 && slot < PAGE_SIZE;
    }

    public static String pageIndicator(int page, int totalItems) {
        return (page + 1) + "/" + pageCount(totalItems);
    }

    public static String money(double value) {
        return String.format(Locale.ROOT, "%.2f", Money.round(value));
    }

    public static String percent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", Money.round(value));
    }
}
