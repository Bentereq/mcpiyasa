package com.mcpiyasa.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** MCPiyasa envanterlerini baska eklentilerin envanterlerinden ayiran kimlik. */
public final class MenuHolder implements InventoryHolder {
    public final MenuType type;
    public final String category;
    public final int page;
    public final String itemId;
    public final MenuType origin;
    public final List<String> moversOrder;
    /** Yalniz admin item duzenleyicisinde: silme onay adimi acik mi. */
    public final boolean adminConfirm;

    public Inventory inventory;

    public MenuHolder(MenuType type, String category, int page, String itemId) {
        this(type, category, page, itemId, null);
    }

    public MenuHolder(MenuType type, String category, int page, String itemId,
                      List<String> moversOrder) {
        this(type, category, page, itemId, null, moversOrder);
    }

    public MenuHolder(MenuType type, String category, int page, String itemId,
                      MenuType origin, List<String> moversOrder) {
        this(type, category, page, itemId, origin, moversOrder, false);
    }

    /** Admin item duzenleyicisi icin silme onay durumunu tasiyan yapici. */
    public MenuHolder(MenuType type, String category, int page, String itemId,
                      boolean adminConfirm) {
        this(type, category, page, itemId, null, null, adminConfirm);
    }

    public MenuHolder(MenuType type, String category, int page, String itemId,
                      MenuType origin, List<String> moversOrder,
                      boolean adminConfirm) {
        this.type = type;
        this.category = category;
        this.page = page;
        this.itemId = itemId;
        this.origin = origin;
        this.moversOrder = moversOrder == null
            ? null : Collections.unmodifiableList(
                new ArrayList<String>(moversOrder));
        this.adminConfirm = adminConfirm;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
