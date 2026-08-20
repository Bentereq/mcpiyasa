package com.mcpiyasa.gui;

/**
 * Admin menulerinin Bukkit'ten bagimsiz slot yerlesimi. Sayfalama, oyuncu
 * menuleriyle ayni {@link Icons} kurallarini kullanir; burada yalniz admin'e
 * ozgu baslik/dugme slotlari ve saf eslemeler bulunur.
 */
public final class AdminLayout {
    // ADMIN_MAIN
    public static final int MAIN_SIZE = 54;
    public static final int STATUS_SLOT = 0;
    public static final int RELOAD_SLOT = 2;
    public static final int STATUS_BUTTON_SLOT = 4;
    public static final int ADD_HELD_SLOT = 6;
    public static final int RESET_INFO_SLOT = 8;
    public static final int MAIN_CATEGORY_START = 9;
    /** 9..44 arasi kategori slotu; 16 kategori tavani buraya sigar. */
    public static final int MAIN_CATEGORY_END = 44;

    // ADMIN_CATEGORY: oyuncu kategorisiyle ayni slotlar (Icons).
    public static final int CATEGORY_SIZE = 54;

    // ADMIN_ITEM
    public static final int ITEM_SIZE = 54;
    public static final int ITEM_ICON_SLOT = 4;
    public static final int STEP_HALVE_SLOT = 19;
    public static final int STEP_MINUS_TEN_SLOT = 20;
    public static final int STEP_MINUS_ONE_SLOT = 21;
    public static final int STEP_PLUS_ONE_SLOT = 23;
    public static final int STEP_PLUS_TEN_SLOT = 24;
    public static final int STEP_DOUBLE_SLOT = 25;
    public static final int TOGGLE_BUY_SLOT = 30;
    public static final int TOGGLE_SELL_SLOT = 32;
    /** Urunu tumuyle devre disi birak / aktiflestir dugmesi. */
    public static final int TOGGLE_ACTIVE_SLOT = 34;
    public static final int ITEM_BACK_SLOT = 45;
    public static final int ITEM_RESET_SLOT = 49;
    public static final int ITEM_REMOVE_SLOT = 53;

    private AdminLayout() {
    }

    public static int mainCategorySlot(int categoryIndex) {
        if (categoryIndex < 0) {
            return -1;
        }
        int slot = MAIN_CATEGORY_START + categoryIndex;
        return slot > MAIN_CATEGORY_END ? -1 : slot;
    }

    public static int mainCategoryIndex(int slot) {
        if (slot < MAIN_CATEGORY_START || slot > MAIN_CATEGORY_END) {
            return -1;
        }
        return slot - MAIN_CATEGORY_START;
    }

    /** Item duzenleyicisindeki fiyat adim slotunu adima cevirir. */
    public static PriceStep priceStepAt(int slot) {
        switch (slot) {
            case STEP_HALVE_SLOT:
                return PriceStep.HALVE;
            case STEP_MINUS_TEN_SLOT:
                return PriceStep.MINUS_TEN_PERCENT;
            case STEP_MINUS_ONE_SLOT:
                return PriceStep.MINUS_ONE;
            case STEP_PLUS_ONE_SLOT:
                return PriceStep.PLUS_ONE;
            case STEP_PLUS_TEN_SLOT:
                return PriceStep.PLUS_TEN_PERCENT;
            case STEP_DOUBLE_SLOT:
                return PriceStep.DOUBLE;
            default:
                return null;
        }
    }
}
