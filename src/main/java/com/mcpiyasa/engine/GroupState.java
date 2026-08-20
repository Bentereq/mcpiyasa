package com.mcpiyasa.engine;

/** Bir grubun degisken calisma durumu. Motor ve state() ile canli nesneyi alan cagiranlar degistirir. */
public final class GroupState {
    public final String groupId;
    public double stock;
    public double epsilon;

    public GroupState(String groupId, double stock, double epsilon) {
        this.groupId = groupId;
        this.stock = stock;
        this.epsilon = epsilon;
    }
}
