package com.mcpiyasa.market;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reload sirasinda canli sinyal birikimlerini degismez olarak tasir. */
public final class SignalTrackerSnapshot {
    private final Map<String, GroupSnapshot> groups;
    final boolean hasCurrentSlot;
    final int currentSlot;
    final int lastClosedSlot;
    final boolean hasCurrentDay;
    final String currentDayKey;

    SignalTrackerSnapshot(Map<String, GroupSnapshot> groups,
                          boolean hasCurrentSlot,
                          int currentSlot,
                          int lastClosedSlot,
                          boolean hasCurrentDay,
                          String currentDayKey) {
        this.groups = Collections.unmodifiableMap(
            new LinkedHashMap<String, GroupSnapshot>(groups));
        this.hasCurrentSlot = hasCurrentSlot;
        this.currentSlot = currentSlot;
        this.lastClosedSlot = lastClosedSlot;
        this.hasCurrentDay = hasCurrentDay;
        this.currentDayKey = currentDayKey;
    }

    Map<String, GroupSnapshot> groups() {
        return groups;
    }

    static final class GroupSnapshot {
        final double[] profileEma;
        final int[] profileCount;
        final double slotVolume;
        final double dayVolume;
        final Map<String, Double> concentrationEntries;

        GroupSnapshot(double[] profileEma,
                      int[] profileCount,
                      double slotVolume,
                      double dayVolume,
                      Map<String, Double> concentrationEntries) {
            this.profileEma = profileEma.clone();
            this.profileCount = profileCount.clone();
            this.slotVolume = slotVolume;
            this.dayVolume = dayVolume;
            this.concentrationEntries = Collections.unmodifiableMap(
                new LinkedHashMap<String, Double>(concentrationEntries));
        }
    }
}
