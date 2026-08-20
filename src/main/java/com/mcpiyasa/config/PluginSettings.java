package com.mcpiyasa.config;

import com.mcpiyasa.engine.EngineParams;

/** Eklentinin dogrulanmis ve degismez yapilandirma ayarlari. */
public final class PluginSettings {
    public final EngineParams engineParams;
    public final boolean kriptoGosterim;
    public final boolean komutTicaret;
    public final boolean tabelaMarket;
    public final boolean npcMarket;
    /** Varsayilan true: creative moddaki oyuncuya karisilmaz. */
    public final boolean creativeTicaret;
    /** Varsayilan true: /market admin metin alt komutlari acik. */
    public final boolean adminKomut;
    public final boolean zorlaCalistir;
    public final int tickDakika;
    public final String dil;
    public final double varsayilanTabanStok;
    public final double profilAlpha;
    public final int profilWarmup;
    /**
     * Fiyatin islem hacmine tepki hizini olceklendiren kuresel carpan.
     * Dusuk deger = sakin/yavas (kalabalik sunucu), yuksek = hassas/hizli
     * (kucuk sunucu). Tum gruplari esit olceklendirdigi icin arbitraji acmaz.
     */
    public final double hassasiyet;
    /**
     * Bir taban fiyatin (grup taban-fiyat/bagimsiz item taban-fiyat)
     * asamayacagi ust sinir. long-cent birikiminin tasmasini onlemek icin
     * ItemsLoader yukleme aninda, AdminCommands yazma aninda bu tavani
     * uygular.
     */
    public final double maxTabanFiyat;

    public PluginSettings(EngineParams engineParams,
                          boolean kriptoGosterim,
                          boolean komutTicaret,
                          boolean tabelaMarket,
                          boolean npcMarket,
                          boolean zorlaCalistir,
                          int tickDakika,
                          String dil,
                          double varsayilanTabanStok,
                          double profilAlpha,
                          int profilWarmup) {
        this(engineParams, kriptoGosterim, komutTicaret, tabelaMarket,
            npcMarket, true, zorlaCalistir, tickDakika, dil,
            varsayilanTabanStok, profilAlpha, profilWarmup);
    }

    public PluginSettings(EngineParams engineParams,
                          boolean kriptoGosterim,
                          boolean komutTicaret,
                          boolean tabelaMarket,
                          boolean npcMarket,
                          boolean creativeTicaret,
                          boolean zorlaCalistir,
                          int tickDakika,
                          String dil,
                          double varsayilanTabanStok,
                          double profilAlpha,
                          int profilWarmup) {
        this(engineParams, kriptoGosterim, komutTicaret, tabelaMarket,
            npcMarket, creativeTicaret, zorlaCalistir, tickDakika, dil,
            varsayilanTabanStok, profilAlpha, profilWarmup, 1.0);
    }

    public PluginSettings(EngineParams engineParams,
                          boolean kriptoGosterim,
                          boolean komutTicaret,
                          boolean tabelaMarket,
                          boolean npcMarket,
                          boolean creativeTicaret,
                          boolean zorlaCalistir,
                          int tickDakika,
                          String dil,
                          double varsayilanTabanStok,
                          double profilAlpha,
                          int profilWarmup,
                          double hassasiyet) {
        this(engineParams, kriptoGosterim, komutTicaret, tabelaMarket,
            npcMarket, creativeTicaret, zorlaCalistir, tickDakika, dil,
            varsayilanTabanStok, profilAlpha, profilWarmup, hassasiyet, true);
    }

    public PluginSettings(EngineParams engineParams,
                          boolean kriptoGosterim,
                          boolean komutTicaret,
                          boolean tabelaMarket,
                          boolean npcMarket,
                          boolean creativeTicaret,
                          boolean zorlaCalistir,
                          int tickDakika,
                          String dil,
                          double varsayilanTabanStok,
                          double profilAlpha,
                          int profilWarmup,
                          double hassasiyet,
                          boolean adminKomut) {
        this(engineParams, kriptoGosterim, komutTicaret, tabelaMarket,
            npcMarket, creativeTicaret, zorlaCalistir, tickDakika, dil,
            varsayilanTabanStok, profilAlpha, profilWarmup, hassasiyet,
            adminKomut, 1_000_000_000.0);
    }

    public PluginSettings(EngineParams engineParams,
                          boolean kriptoGosterim,
                          boolean komutTicaret,
                          boolean tabelaMarket,
                          boolean npcMarket,
                          boolean creativeTicaret,
                          boolean zorlaCalistir,
                          int tickDakika,
                          String dil,
                          double varsayilanTabanStok,
                          double profilAlpha,
                          int profilWarmup,
                          double hassasiyet,
                          boolean adminKomut,
                          double maxTabanFiyat) {
        this.engineParams = engineParams;
        this.kriptoGosterim = kriptoGosterim;
        this.komutTicaret = komutTicaret;
        this.tabelaMarket = tabelaMarket;
        this.npcMarket = npcMarket;
        this.creativeTicaret = creativeTicaret;
        this.adminKomut = adminKomut;
        this.zorlaCalistir = zorlaCalistir;
        this.tickDakika = tickDakika;
        this.dil = dil;
        this.varsayilanTabanStok = varsayilanTabanStok;
        this.profilAlpha = profilAlpha;
        this.profilWarmup = profilWarmup;
        this.hassasiyet = hassasiyet;
        this.maxTabanFiyat = maxTabanFiyat;
    }
}
