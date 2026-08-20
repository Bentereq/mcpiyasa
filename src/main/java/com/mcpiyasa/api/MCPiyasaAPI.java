package com.mcpiyasa.api;

import com.mcpiyasa.engine.TradeSide;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.List;

/** MCPiyasa'nin diger eklentilere sundugu kararlı gelistirici yuzeyi. */
public interface MCPiyasaAPI {
    /**
     * Item icin degismez bir fiyat teklifi dondurur. {@code total} para
     * biriminde, {@code unitAvg} para/item birimindedir ve ikisi de iki
     * ondaliklidir. Yapilandirilmis azami islem adedini asan {@code amount},
     * {@link #trade(Player, Material, int, TradeSide)} ile ayni sekilde azami
     * degere kelepcelenir; DTO'daki {@code amount} uygulanan adettir.
     *
     * @throws IllegalArgumentException item null/bilinmiyorsa, side null ise
     *         veya kelepceleme sonrasinda adet pozitif degilse
     * @throws IllegalStateException istenen item yonu kapaliysa ({@code yon
     *         kapali}), Bukkit main thread'i disinda veya API runtime'i
     *         kullanilamaz durumdayken cagrilirsa
     */
    PriceQuoteDto getQuote(Material item, int amount, TradeSide side);

    /**
     * Item'in guncel orta fiyatini para/item biriminde, iki ondalikli dondurur.
     *
     * @throws IllegalArgumentException item null veya bilinmiyorsa
     * @throws IllegalStateException Bukkit main thread'i disinda veya API
     *         runtime'i kullanilamaz durumdayken cagrilirsa
     */
    BigDecimal getPrice(Material item);

    /**
     * Islemi yalniz Bukkit main thread'inde yurutur. Yapilandirilmis azami
     * islem adedini asan {@code amount}, getQuote ile ayni azami degere
     * kelepcelenir. Sonuctaki {@code total}, teklif olusmussa basarisiz
     * sonuclarda da teklifin para tutaridir; teklif olusmadan reddedilen
     * sonuclarda {@code 0.00}'dir ve basarisiz sonuc para aktarildigi anlamina
     * gelmez. Bilinmeyen item exception yerine {@code BILINMEYEN_ITEM} sonucu
     * dondurur. Plugin runtime'i kullanilamaz durumdaysa para veya envantere
     * dokunmadan {@code UNAVAILABLE} sonucu dondurur.
     *
     * @throws IllegalStateException Bukkit main thread'i disinda cagrilirsa
     */
    TradeResultDto trade(Player player, Material item, int amount, TradeSide side);

    /**
     * Son {@code days} yerel takvim gunu icin eskiden yeniye fiyat noktalarini
     * dondurur. Her {@code double[]} degeri {@code {gunSirasi, mid}} seklindedir;
     * gunSirasi boyutsuz ve sifirdan baslar, mid para/item birimindedir. Snapshot
     * olmayan gun son bilinen degeri tasir; henuz bir deger yoksa o gun atlanir.
     * Pozitif olmayan {@code days} bos liste dondurur.
     *
     * <p>Bu DB tabanli sorgu main thread disindan da cagrilabilir; cagiran
     * thread sorgu tamamlanana kadar bloklanir.</p>
     *
     * @throws IllegalArgumentException item null veya bilinmiyorsa
     * @throws IllegalStateException Clock.dayKey() gecerli ISO tarih degilse
     *         veya API runtime'i kullanilamaz durumdaysa
     */
    List<double[]> getPriceHistory(Material item, int days);

    /**
     * Item'in sunucunun yerel gece yarisindan
     * ({@link com.mcpiyasa.market.Clock#dayKey()} gununun basindan) itibaren
     * basariyla tamamlanan BUY ve SELL islemlerindeki ham item adetlerinin
     * toplamini dondurur. Sonuc item adedi birimindedir; grup, oyuncu veya item
     * agirligi ile olceklenmez.
     *
     * <p>Bu DB tabanli sorgu main thread disindan da cagrilabilir; cagiran
     * thread sorgu tamamlanana kadar bloklanir.</p>
     *
     * @throws IllegalArgumentException item null veya bilinmiyorsa
     * @throws IllegalStateException Clock.dayKey() gecerli ISO tarih degilse
     *         veya API runtime'i kullanilamaz durumdaysa
     */
    double getDailyVolume(Material item);
}
