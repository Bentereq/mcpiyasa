package com.mcpiyasa.access;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Citizens NPC market trait'inin istege bagli kayit noktasi. */
public final class NpcHook {
    private static volatile MainMenuOpener mainMenuOpener;

    private NpcHook() {
    }

    /**
     * Citizens yukluyse market trait'ini kaydeder.
     *
     * <p>Citizens trait'leri no-arg constructor ile olusturdugu icin menu
     * callback'i bu statik kopruye verilir. Bukkit event'i ve kayit islemi
     * main thread'de calisir.</p>
     */
    public static boolean tryRegister(Plugin plugin, MainMenuOpener opener) {
        if (plugin == null || opener == null) {
            throw new IllegalArgumentException("plugin ve opener null olamaz");
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            plugin.getLogger().info("Citizens yok, NPC marketi devre disi");
            return false;
        }

        boolean registered = CitizensRegistration.register();
        if (registered) {
            setMainMenuOpener(opener);
        }
        return registered;
    }

    private static void setMainMenuOpener(MainMenuOpener opener) {
        mainMenuOpener = opener;
    }

    static void openMainMenuFor(Player player) {
        MainMenuOpener opener = mainMenuOpener;
        if (opener != null) {
            opener.open(player);
        }
    }

    public interface MainMenuOpener {
        void open(Player player);
    }

    /** Yalniz Citizens varlik kontrolunden sonra yuklenir. */
    private static final class CitizensRegistration {
        private CitizensRegistration() {
        }

        private static boolean register() {
            net.citizensnpcs.api.trait.TraitFactory traitFactory =
                net.citizensnpcs.api.CitizensAPI.getTraitFactory();
            if (traitFactory.getTraitClass("mcpiyasa") != null) {
                return true;
            }

            traitFactory.registerTrait(
                net.citizensnpcs.api.trait.TraitInfo.create(NpcTrait.class)
                    .withName("mcpiyasa"));
            return true;
        }
    }
}
