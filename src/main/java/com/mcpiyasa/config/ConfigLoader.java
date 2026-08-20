package com.mcpiyasa.config;

import com.mcpiyasa.engine.EngineParams;
import java.util.List;
import org.bukkit.configuration.Configuration;

/** Bukkit yapilandirmasini dogrulanmis eklenti ayarlarina donusturur. */
public final class ConfigLoader {
    /** Tek islemin teklif dongusu O(adet) ve ana thread'de calisir. */
    public static final int MAX_ISLEM_ADET_TAVANI = 100000;

    private ConfigLoader() {
    }

    public static PluginSettings load(Configuration cfg) {
        double sigma = cfg.getDouble("motor.spread", 0.10);
        double lambda = cfg.getDouble("motor.toparlanma-katsayisi", 0.05);
        int tickDakika = cfg.getInt("motor.toparlanma-dakika", 10);
        double kappa = cfg.getDouble("motor.anormallik-ussu", 0.5);
        double[] anomalyBand = readBand(cfg, "motor.anormallik-bant", 0.5, 3.0);
        double hhiGamma = cfg.getDouble("motor.hhi-etki", 1.0);
        double[] elasticityBand = readBand(cfg, "motor.esneklik-bant", 0.25, 1.5);
        double epsBase = cfg.getDouble("motor.esneklik-taban", 0.6);
        double refDailyVolume = cfg.getDouble("motor.referans-gunluk-hacim", 2000.0);
        double[] priceBand = readBand(cfg, "motor.fiyat-bant", 0.25, 4.0);
        int maxTradeAmount = cfg.getInt("motor.max-islem-adet", 2304);
        double defaultStock = cfg.getDouble(
            "motor.varsayilan-taban-stok", 20000.0);
        double profileAlpha = cfg.getDouble("motor.profil-alpha", 0.3);
        int profileWarmup = cfg.getInt("motor.profil-isinma-slot", 3);
        double hassasiyet = cfg.getDouble("motor.hassasiyet", 1.0);
        double maxTabanFiyat = cfg.getDouble(
            "motor.max-taban-fiyat", 1_000_000_000.0);

        if (!Double.isFinite(sigma) || sigma < 0.02 || sigma > 1.0) {
            throw invalid(
                "motor.spread",
                sigma,
                "0.02 ile 1.0 arasinda olmali; yaklasik %1.7 altindaki "
                    + "spread capraz-agirlik konvekslik arbitraji uretebilir");
        }
        requireRange("motor.toparlanma-katsayisi", lambda, 0.0, 1.0);
        if (tickDakika < 1) {
            throw invalid("motor.toparlanma-dakika", tickDakika, "en az 1 olmali");
        }
        requireFiniteMinimum("motor.anormallik-ussu", kappa, 0.0);
        requireFiniteMinimum("motor.hhi-etki", hhiGamma, 0.0);
        requireRange("motor.esneklik-taban", epsBase, elasticityBand[0], elasticityBand[1]);
        requirePositiveFinite(
            "motor.referans-gunluk-hacim", refDailyVolume);
        if (maxTradeAmount < 1 || maxTradeAmount > MAX_ISLEM_ADET_TAVANI) {
            throw invalid(
                "motor.max-islem-adet",
                maxTradeAmount,
                "1 ile " + MAX_ISLEM_ADET_TAVANI + " arasinda olmali");
        }
        requirePositiveFinite(
            "motor.varsayilan-taban-stok", defaultStock);
        if (!Double.isFinite(profileAlpha)
                || !(profileAlpha > 0.0 && profileAlpha <= 1.0)) {
            throw invalid(
                "motor.profil-alpha",
                profileAlpha,
                "0'dan buyuk ve 1'den kucuk veya esit olmali");
        }
        if (profileWarmup < 1) {
            throw invalid(
                "motor.profil-isinma-slot", profileWarmup, "en az 1 olmali");
        }
        if (!Double.isFinite(hassasiyet) || !(hassasiyet > 0.0)) {
            throw invalid(
                "motor.hassasiyet",
                hassasiyet,
                "sonlu ve 0'dan buyuk olmali");
        }
        requirePositiveFinite("motor.max-taban-fiyat", maxTabanFiyat);

        EngineParams engineParams = new EngineParams(
            sigma,
            lambda,
            kappa,
            anomalyBand[0],
            anomalyBand[1],
            hhiGamma,
            elasticityBand[0],
            elasticityBand[1],
            epsBase,
            priceBand[0],
            priceBand[1],
            refDailyVolume,
            maxTradeAmount
        );

        return new PluginSettings(
            engineParams,
            cfg.getBoolean("ozellikler.kripto-gosterim", false),
            cfg.getBoolean("ozellikler.komut-ticaret", false),
            cfg.getBoolean("ozellikler.tabela-market", false),
            cfg.getBoolean("ozellikler.npc-market", false),
            cfg.getBoolean("ozellikler.creative-ticaret", true),
            cfg.getBoolean("guvenli-mod.zorla-calistir", false),
            tickDakika,
            cfg.getString("dil", "tr"),
            defaultStock,
            profileAlpha,
            profileWarmup,
            hassasiyet,
            cfg.getBoolean("ozellikler.admin-komut", true),
            maxTabanFiyat
        );
    }

    private static double[] readBand(Configuration cfg, String key,
                                     double defaultLower, double defaultUpper) {
        if (!cfg.contains(key)) {
            return new double[] {defaultLower, defaultUpper};
        }

        List<Double> values = cfg.getDoubleList(key);
        if (values.size() != 2) {
            throw invalid(key, cfg.get(key), "iki sayi icermeli");
        }

        double lower = values.get(0);
        double upper = values.get(1);
        if (!Double.isFinite(lower) || !Double.isFinite(upper)
                || !(lower > 0.0 && lower < upper)) {
            throw invalid(
                key,
                values,
                "sonlu, pozitif ve alt deger ust degerden kucuk olmali");
        }
        return new double[] {lower, upper};
    }

    private static void requireRange(String key, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw invalid(key, value, min + " ile " + max + " arasinda olmali");
        }
    }

    private static void requireFiniteMinimum(
            String key, double value, double minimum) {
        if (!Double.isFinite(value) || value < minimum) {
            throw invalid(
                key, value, "sonlu ve en az " + minimum + " olmali");
        }
    }

    private static void requirePositiveFinite(String key, double value) {
        if (!Double.isFinite(value) || !(value > 0.0)) {
            throw invalid(key, value, "sonlu ve 0'dan buyuk olmali");
        }
    }

    private static IllegalArgumentException invalid(String key, Object value, String detail) {
        return new IllegalArgumentException(key + ": " + detail + " (bulunan: " + value + ")");
    }
}
