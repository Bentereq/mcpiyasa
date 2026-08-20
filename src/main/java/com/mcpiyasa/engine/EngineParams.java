package com.mcpiyasa.engine;

/** Fiyat motorunun degismez ayar kumesi. */
public final class EngineParams {
    public final double sigma;
    public final double lambda;
    public final double kappa;
    public final double anomalyMin;
    public final double anomalyMax;
    public final double hhiGamma;
    public final double epsMin;
    public final double epsMax;
    public final double epsBase;
    public final double bandMin;
    public final double bandMax;
    public final double refDailyVolume;
    public final int maxTradeAmount;

    public EngineParams(double sigma, double lambda, double kappa,
                        double anomalyMin, double anomalyMax, double hhiGamma,
                        double epsMin, double epsMax, double epsBase,
                        double bandMin, double bandMax,
                        double refDailyVolume, int maxTradeAmount) {
        this.sigma = sigma;
        this.lambda = lambda;
        this.kappa = kappa;
        this.anomalyMin = anomalyMin;
        this.anomalyMax = anomalyMax;
        this.hhiGamma = hhiGamma;
        this.epsMin = epsMin;
        this.epsMax = epsMax;
        this.epsBase = epsBase;
        this.bandMin = bandMin;
        this.bandMax = bandMax;
        this.refDailyVolume = refDailyVolume;
        this.maxTradeAmount = maxTradeAmount;
    }
}
