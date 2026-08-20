package com.mcpiyasa.api;

/** Placeholder fiyatlarini salt-okunur olarak sunan kucuk bagimlilik yuzeyi. */
public interface PriceView {
    double mid(String id);
    double buyUnit(String id);
    double sellUnit(String id);
    double change24h(String id);
    boolean knows(String id);
}
