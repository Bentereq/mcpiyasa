package com.mcpiyasa.config;

/** Bir market kategorisinin degismez tanimi. */
public final class CategoryDef {
    public final String id;
    public final String iconMaterial;
    public final int sira;

    public CategoryDef(String id, String iconMaterial, int sira) {
        this.id = id;
        this.iconMaterial = iconMaterial;
        this.sira = sira;
    }
}
