package com.mcpiyasa.engine;

/** Islem hacmine gore fiyat esnekligini belirler. */
public final class Calibration {
    private Calibration() {}

    public static double epsilonFor(EngineParams p, double emaDailyVolume) {
        double volume = Math.max(emaDailyVolume, 1.0);
        double epsilon = p.epsBase * Math.sqrt(p.refDailyVolume / volume);
        return Math.min(p.epsMax, Math.max(p.epsMin, epsilon));
    }
}
