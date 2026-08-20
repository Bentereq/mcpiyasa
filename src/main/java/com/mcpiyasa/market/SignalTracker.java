package com.mcpiyasa.market;

import com.mcpiyasa.engine.Concentration;
import com.mcpiyasa.engine.TradeContext;
import com.mcpiyasa.engine.VolumeProfile;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Main thread'de kullanilan, Bukkit'ten bagimsiz piyasa sinyal biriktiricisi. */
public final class SignalTracker {
    private static final int SLOT_COUNT = 168;
    private static final String PREVIEW_SELLER_ID = "<preview-seller>";

    private final double profileAlpha;
    private final int warmup;
    private final Map<String, VolumeProfile> profiles =
        new LinkedHashMap<String, VolumeProfile>();
    private final Map<String, Concentration> concentrations =
        new LinkedHashMap<String, Concentration>();
    private final Map<String, Double> slotVolumes =
        new LinkedHashMap<String, Double>();
    private final Map<String, Double> dayVolumes =
        new LinkedHashMap<String, Double>();

    private boolean hasCurrentSlot;
    private int currentSlot;
    private int lastClosedSlot = -1;
    private boolean hasCurrentDay;
    private String currentDayKey;

    public SignalTracker(double profileAlpha, int warmup) {
        this.profileAlpha = profileAlpha;
        this.warmup = warmup;
    }

    public void onTrade(String groupId, String playerId, double volume, boolean isSell) {
        if (groupId == null || !(volume > 0.0) || !Double.isFinite(volume)) {
            return;
        }
        ensureGroup(groupId);
        addVolume(slotVolumes, groupId, volume);
        addVolume(dayVolumes, groupId, volume);
        if (isSell) {
            concentrations.get(groupId).record(playerId, volume);
        }
    }

    public TradeContext context(String groupId, int slotIndex, double slotFraction) {
        return contextForTrade(
            groupId, null, 0.0, false, slotIndex, slotFraction);
    }

    /**
     * Emrin agirlikli hacmini anomaly ve SELL concentration hesabina dahil
     * eder; gercek tracker durumunu degistirmez.
     */
    public TradeContext contextForTrade(String groupId,
                                        String playerId,
                                        double projectedWeightedVolume,
                                        boolean isSell,
                                        int slotIndex,
                                        double slotFraction) {
        if (!(projectedWeightedVolume >= 0.0)
                || !Double.isFinite(projectedWeightedVolume)) {
            throw new IllegalArgumentException(
                "Projected hacim sonlu ve negatif olmayan olmali");
        }
        int normalizedSlot = normalizeSlot(slotIndex);
        Double actual = slotVolumes.get(groupId);
        double actualSoFar = (actual == null ? 0.0 : actual.doubleValue())
            + projectedWeightedVolume;
        VolumeProfile profile = profiles.get(groupId);
        if (profile == null) {
            profile = new VolumeProfile(profileAlpha, warmup);
        }
        Concentration concentration = concentrations.get(groupId);
        if (concentration == null) {
            concentration = new Concentration();
        }
        String projectedSeller = playerId == null
            ? PREVIEW_SELLER_ID : playerId;
        double hhi = isSell
            ? concentration.projectedHhi(
                projectedSeller, projectedWeightedVolume)
            : concentration.hhi();
        int activeSellers = isSell
            ? concentration.projectedActiveSellers(
                projectedSeller, projectedWeightedVolume)
            : concentration.activeSellers();
        return new TradeContext(
            profile.anomalyRatio(normalizedSlot, actualSoFar, slotFraction),
            hhi,
            activeSellers
        );
    }

    /** Tum canli profil, hacim, concentration ve zaman durumunu kopyalar. */
    public SignalTrackerSnapshot snapshot() {
        Set<String> groupIds = new LinkedHashSet<String>();
        groupIds.addAll(profiles.keySet());
        groupIds.addAll(concentrations.keySet());
        groupIds.addAll(slotVolumes.keySet());
        groupIds.addAll(dayVolumes.keySet());
        Map<String, SignalTrackerSnapshot.GroupSnapshot> groups =
            new LinkedHashMap<String, SignalTrackerSnapshot.GroupSnapshot>();
        for (String groupId : groupIds) {
            VolumeProfile profile = profiles.get(groupId);
            Concentration concentration = concentrations.get(groupId);
            if (profile == null || concentration == null) {
                continue;
            }
            groups.put(groupId, new SignalTrackerSnapshot.GroupSnapshot(
                profile.emaSnapshot(),
                profile.countSnapshot(),
                volumeOf(slotVolumes, groupId),
                volumeOf(dayVolumes, groupId),
                concentration.entries()));
        }
        return new SignalTrackerSnapshot(
            groups,
            hasCurrentSlot,
            currentSlot,
            lastClosedSlot,
            hasCurrentDay,
            currentDayKey);
    }

    /**
     * Yalniz candidate'da bulunan gruplari snapshot'tan devralir. Yeni
     * gruplar bos profil/sinyal durumuyla baslar, silinenler atilir.
     */
    public void restoreSnapshot(SignalTrackerSnapshot snapshot,
                                Iterable<String> candidateGroupIds) {
        if (snapshot == null || candidateGroupIds == null) {
            throw new IllegalArgumentException(
                "Signal snapshot restore bagimliliklari null olamaz");
        }
        profiles.clear();
        concentrations.clear();
        slotVolumes.clear();
        dayVolumes.clear();
        for (String groupId : candidateGroupIds) {
            ensureGroup(groupId);
            SignalTrackerSnapshot.GroupSnapshot saved =
                snapshot.groups().get(groupId);
            if (saved == null) {
                continue;
            }
            profiles.get(groupId).restore(
                saved.profileEma, saved.profileCount);
            concentrations.get(groupId).restore(
                saved.concentrationEntries);
            if (saved.slotVolume > 0.0) {
                slotVolumes.put(groupId, Double.valueOf(saved.slotVolume));
            }
            if (saved.dayVolume > 0.0) {
                dayVolumes.put(groupId, Double.valueOf(saved.dayVolume));
            }
        }
        hasCurrentSlot = snapshot.hasCurrentSlot;
        currentSlot = snapshot.currentSlot;
        lastClosedSlot = snapshot.lastClosedSlot;
        hasCurrentDay = snapshot.hasCurrentDay;
        currentDayKey = snapshot.currentDayKey;
    }

    /**
     * Yeni zaman dilimini benimser, gerekiyorsa onceki dilimi ve gunu kapatir.
     * Ilk cagri onceki slot bilinmedigi icin bir profil kaydi uretmez.
     */
    public Map<String, Double> minuteTick(int slotIndex, String dayKey) {
        int normalizedSlot = normalizeSlot(slotIndex);
        if (!hasCurrentSlot) {
            currentSlot = normalizedSlot;
            hasCurrentSlot = true;
        } else if (currentSlot != normalizedSlot) {
            for (Map.Entry<String, VolumeProfile> entry : profiles.entrySet()) {
                Double volume = slotVolumes.get(entry.getKey());
                entry.getValue().record(
                    currentSlot,
                    volume == null ? 0.0 : volume.doubleValue()
                );
            }
            slotVolumes.clear();
            lastClosedSlot = currentSlot;
            currentSlot = normalizedSlot;
        }

        if (!hasCurrentDay) {
            currentDayKey = dayKey;
            hasCurrentDay = true;
            return Collections.emptyMap();
        }
        if (sameDay(currentDayKey, dayKey)) {
            return Collections.emptyMap();
        }

        Map<String, Double> closedDayVolumes = new LinkedHashMap<String, Double>();
        for (String groupId : profiles.keySet()) {
            Double volume = dayVolumes.get(groupId);
            closedDayVolumes.put(
                groupId,
                Double.valueOf(volume == null ? 0.0 : volume.doubleValue())
            );
        }
        dayVolumes.clear();
        for (Concentration concentration : concentrations.values()) {
            concentration.resetDay();
        }
        currentDayKey = dayKey;
        return closedDayVolumes;
    }

    public VolumeProfile profile(String groupId) {
        ensureGroup(groupId);
        return profiles.get(groupId);
    }

    public Concentration concentration(String groupId) {
        ensureGroup(groupId);
        return concentrations.get(groupId);
    }

    int lastClosedSlot() {
        return lastClosedSlot;
    }

    private void ensureGroup(String groupId) {
        if (!profiles.containsKey(groupId)) {
            profiles.put(groupId, new VolumeProfile(profileAlpha, warmup));
            concentrations.put(groupId, new Concentration());
        }
    }

    private static void addVolume(Map<String, Double> volumes,
                                  String groupId,
                                  double volume) {
        Double current = volumes.get(groupId);
        volumes.put(
            groupId,
            Double.valueOf(current == null ? volume : current.doubleValue() + volume)
        );
    }

    private static double volumeOf(Map<String, Double> volumes,
                                   String groupId) {
        Double volume = volumes.get(groupId);
        return volume == null ? 0.0 : volume.doubleValue();
    }

    private static int normalizeSlot(int slotIndex) {
        return Math.floorMod(slotIndex, SLOT_COUNT);
    }

    private static boolean sameDay(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
