package com.mcpiyasa.engine;

/** Bir fiyat grubunun degismez tanimi. */
public final class GroupDef {
    public final String id;
    public final double basePrice;
    public final double baseStock;

    public GroupDef(String id, double basePrice, double baseStock) {
        this.id = id;
        this.basePrice = basePrice;
        this.baseStock = baseStock;
    }
}
