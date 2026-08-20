package com.mcpiyasa.compat;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.KnowledgeBookMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.SuspiciousStewMeta;
import org.bukkit.inventory.meta.TropicalFishBucketMeta;

public final class Materials {
    private Materials() {
    }

    /**
     * Market anahtari olabilecek materyaller. {@code isItem()} tek basina
     * yetmez: {@code AIR} icin true doner ama {@code maxStackSize == 0}'dir
     * ve teslimat yolu {@code IllegalStateException} atip ucuncu hatada
     * guvenli moda duser (yapilandirma kaynakli DoS).
     */
    public static boolean isMarketable(Material material) {
        return material != null && material.isItem()
            && material.getMaxStackSize() > 0;
    }

    /**
     * Hasar alabilen materyaller icin true. Boyle bir item'in yalnizca
     * sifir hasarli ornekleri {@code isSimilar} esiginden gecer.
     */
    public static boolean isDamageable(Material material) {
        return material != null && material.getMaxDurability() > 0;
    }

    /**
     * Materyalin varsayilan yigini gercekten ozel meta tasiyorsa true.
     *
     * <p>Eski uygulama {@code new ItemStack(material).hasItemMeta()}
     * kullaniyordu; ancak {@code ItemStack(Material)} yapicisi ozel
     * {@code meta} alanini hic set etmez, dolayisiyla canli sunucuda bu
     * cagri POTION/ENCHANTED_BOOK/FIREWORK_ROCKET dahil <em>her</em> item
     * icin false doner ve zorunlu INFO uyarisi olu kod olurdu.
     *
     * <p>Guvenilir sinyal {@code ItemFactory.getItemMeta(material)} turunden
     * gelir: donen meta duz taban item meta'nin otesinde bir alt tur (POTION
     * -> PotionMeta, ENCHANTED_BOOK -> EnchantmentStorageMeta, ...) ya da
     * gercek dayaniklilik tasiyorsa materyal meta-tasiyici sayilir. Duz
     * DIAMOND gibi materyaller false doner.
     *
     * <p>Bukkit {@code ItemFactory} yoksa (ornegin birim testlerinde)
     * false doner. Bu yalnizca bilgilendirici bir sinyaldir; item asla
     * reddedilmez.
     */
    public static boolean defaultStackHasMeta(Material material) {
        if (material == null || !material.isItem()) {
            return false;
        }
        try {
            ItemFactory factory = Bukkit.getItemFactory();
            if (factory == null) {
                return false;
            }
            return isSpecialisedMeta(factory.getItemMeta(material), material);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /**
     * {@code ItemFactory.getItemMeta} sonucunun duz taban item meta'nin
     * otesine gecen ozel bir tur olup olmadigini soyler.
     *
     * <p>{@code Damageable} (ve {@code Repairable}) 1.16.5'te taban
     * {@code CraftMetaItem} tarafindan DIAMOND dahil <em>tum</em> itemlar
     * icin uygulanir; bu yuzden ciplak {@code instanceof Damageable} DIAMOND'i
     * da yakalar. Dayaniklilik bu nedenle yalniz materyalin gercek
     * {@code getMaxDurability() > 0} degeriyle sayilir.
     */
    private static boolean isSpecialisedMeta(ItemMeta meta, Material material) {
        if (meta == null) {
            return false;
        }
        if (meta instanceof PotionMeta
                || meta instanceof EnchantmentStorageMeta
                || meta instanceof BookMeta
                || meta instanceof FireworkMeta
                || meta instanceof FireworkEffectMeta
                || meta instanceof SkullMeta
                || meta instanceof LeatherArmorMeta
                || meta instanceof BannerMeta
                || meta instanceof MapMeta
                || meta instanceof TropicalFishBucketMeta
                || meta instanceof SuspiciousStewMeta
                || meta instanceof KnowledgeBookMeta) {
            return true;
        }
        return meta instanceof Damageable && material.getMaxDurability() > 0;
    }

    public static Material resolve(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.trim();
        Material material = Material.matchMaterial(normalized);
        if (material == null && normalized.regionMatches(
                true, 0, "minecraft:", 0, "minecraft:".length())) {
            material = Material.matchMaterial(
                normalized.substring("minecraft:".length()));
        }
        return material;
    }
}
