package com.mcpiyasa.engine;

/** Bir islemin anomali ve satici yogunlasmasi sinyalleri. */
public final class TradeContext {
    public static final TradeContext NEUTRAL = new TradeContext(1.0, 0.0, 1);

    public final double anomalyRatio;
    public final double hhi;
    public final int activeSellers;

    public TradeContext(double anomalyRatio, double hhi, int activeSellers) {
        this.anomalyRatio = anomalyRatio;
        this.hhi = hhi;
        this.activeSellers = activeSellers;
    }
}
