package com.mcpiyasa.engine;

/**
 * Tracks an exponential moving volume baseline for each hour of the week.
 * Slot indices are supplied as {@code dayOfWeek * 24 + hour} in the range
 * {@code 0..167}; this class does not read time itself.
 */
public final class VolumeProfile {
    private static final int SLOT_COUNT = 168;
    private static final double MIN_SCALED_EXPECTED = 0.001;

    private final double emaAlpha;
    private final int warmupSlots;
    private final double[] ema = new double[SLOT_COUNT];
    private final int[] count = new int[SLOT_COUNT];

    public VolumeProfile(double emaAlpha, int warmupSlots) {
        this.emaAlpha = emaAlpha;
        this.warmupSlots = warmupSlots;
    }

    public void record(int slotIndex, double closedSlotVolume) {
        if (count[slotIndex] == 0) {
            ema[slotIndex] = closedSlotVolume;
        } else {
            ema[slotIndex] = emaAlpha * closedSlotVolume
                    + (1.0 - emaAlpha) * ema[slotIndex];
        }
        count[slotIndex]++;
    }

    public double expected(int slotIndex) {
        if (count[slotIndex] < warmupSlots) {
            return -1.0;
        }
        return ema[slotIndex];
    }

    public double anomalyRatio(int slotIndex, double actualSoFar, double slotFraction) {
        double expectedVolume = expected(slotIndex);
        if (expectedVolume < 0.0) {
            return 1.0;
        }

        double scaledExpected = expectedVolume * slotFraction;
        if (scaledExpected < MIN_SCALED_EXPECTED) {
            return 1.0;
        }
        return actualSoFar / scaledExpected;
    }

    public double[] emaSnapshot() {
        return ema.clone();
    }

    public int[] countSnapshot() {
        return count.clone();
    }

    public void restore(double[] ema, int[] count) {
        if (ema == null || count == null) {
            throw new IllegalArgumentException("ema and count arrays must not be null");
        }
        if (ema.length != SLOT_COUNT || count.length != SLOT_COUNT) {
            throw new IllegalArgumentException("ema and count arrays must have length 168");
        }
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (!Double.isFinite(ema[slot]) || count[slot] < 0) {
                throw new IllegalArgumentException(
                    "ema values must be finite and counts must not be negative"
                        + " (slot=" + slot + ")");
            }
        }
        System.arraycopy(ema, 0, this.ema, 0, SLOT_COUNT);
        System.arraycopy(count, 0, this.count, 0, SLOT_COUNT);
    }
}
