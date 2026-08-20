package com.mcpiyasa.gui;

import com.mcpiyasa.engine.TradeSide;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Admin GUI'nin yazma/eylem arka yuzu. Butun items.yml yazimi ve reload'u
 * AdminCommands'ta kalir; GUI yalniz bu yuzeyi cagirir, YAML'a dokunmaz.
 */
public interface AdminGuiActions {
    /**
     * Item'in taban birim fiyatini {@code newBaseUnit} yapar; ayni gruplu/
     * bagimsiz yazma yolunu (fiyat) kullanir, tanimi yeniden yukler.
     *
     * @return {eski, yeni} taban birim fiyat; hata halinde {@code null}
     */
    double[] setBaseUnitPrice(CommandSender admin, String itemId,
                              double newBaseUnit);

    /** Alis/satis yonunu ters cevirir, yeniden yukler; yeni acik durumu. */
    boolean toggleDirection(CommandSender admin, String itemId, TradeSide side);

    /**
     * Urunun {@code aktif} bayragini ters cevirir (devre disi birak /
     * aktiflestir), ayni writeItemDefinition tabanli YAML yolunu kullanir ve
     * yeniden yukler.
     *
     * @return yeni aktif durum; hata halinde mevcut durum
     */
    boolean toggleActive(CommandSender admin, String itemId);

    /** Item'i items.yml'den cikarir ve yeniden yukler; basari. */
    boolean removeItem(CommandSender admin, String itemId);

    /** Item grubunu taban stoka sifirlar; basari. */
    boolean resetItem(CommandSender admin, String itemId);

    /**
     * Admin'in ana elindeki materyali varsayilan fiyatla ekler (zaten varsa
     * dokunmaz) ve yeniden yukler.
     *
     * @return eklenen/var olan itemId; el bos ya da hata halinde {@code null}
     */
    String addHeldItem(Player admin);

    /** items.yml + config'i yeniden yukler ve admin'e sonucu bildirir. */
    void reload(CommandSender admin);

    /** Ayrintili durum ozetini admin'e sohbetle yollar. */
    void status(CommandSender admin);
}
