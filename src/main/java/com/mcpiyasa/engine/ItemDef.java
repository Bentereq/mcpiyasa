package com.mcpiyasa.engine;

/** Bir esyanin degismez tanimi. minPrice/maxPrice null ise global bant kullanilir. */
public final class ItemDef {
    public final String id;
    public final String groupId;
    public final double weight;
    public final Double minPrice;
    public final Double maxPrice;
    public final boolean alisAcik;
    public final boolean satisAcik;
    /**
     * items.yml {@code aktif} bayragi. false ise urun tumuyle devre disidir:
     * her oyuncu yuzeyinde bilinmeyen item gibi gorunmez ve takas edilemez,
     * yalniz admin yuzeylerinde yeniden acilmak uzere gorunur. Yon bayraklariyla
     * ayni tanim plumbing'inde tasinir; varsayilan {@code true}.
     */
    public final boolean active;

    public ItemDef(String id, String groupId, double weight, Double minPrice, Double maxPrice) {
        this(id, groupId, weight, minPrice, maxPrice, true, true);
    }

    public ItemDef(String id, String groupId, double weight,
                   Double minPrice, Double maxPrice,
                   boolean alisAcik, boolean satisAcik) {
        this(id, groupId, weight, minPrice, maxPrice, alisAcik, satisAcik, true);
    }

    public ItemDef(String id, String groupId, double weight,
                   Double minPrice, Double maxPrice,
                   boolean alisAcik, boolean satisAcik, boolean active) {
        this.id = id;
        this.groupId = groupId;
        this.weight = weight;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.alisAcik = alisAcik;
        this.satisAcik = satisAcik;
        this.active = active;
    }

    public boolean isTradeEnabled(TradeSide side) {
        return side == TradeSide.BUY ? alisAcik
            : side == TradeSide.SELL && satisAcik;
    }
}
