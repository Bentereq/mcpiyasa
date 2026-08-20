package com.mcpiyasa;

import com.mcpiyasa.access.MarketCommand;
import com.mcpiyasa.access.NpcHook;
import com.mcpiyasa.access.SignShopListener;
import com.mcpiyasa.admin.AdminCommands;
import com.mcpiyasa.admin.ReloadResult;
import com.mcpiyasa.api.ApiFacade;
import com.mcpiyasa.api.ApiImpl;
import com.mcpiyasa.api.EventTradePreHook;
import com.mcpiyasa.api.MCPiyasaAPI;
import com.mcpiyasa.api.PapiHook;
import com.mcpiyasa.compat.SchedulerCompat;
import com.mcpiyasa.config.ConfigLoader;
import com.mcpiyasa.config.ItemNames;
import com.mcpiyasa.config.ItemsLoader;
import com.mcpiyasa.config.Messages;
import com.mcpiyasa.config.ParsedItems;
import com.mcpiyasa.config.PluginSettings;
import com.mcpiyasa.diag.DiagCheck;
import com.mcpiyasa.diag.Diagnostics;
import com.mcpiyasa.diag.SafeMode;
import com.mcpiyasa.engine.EngineParams;
import com.mcpiyasa.engine.EngineSnapshot;
import com.mcpiyasa.engine.GroupState;
import com.mcpiyasa.engine.PriceEngine;
import com.mcpiyasa.engine.VolumeProfile;
import com.mcpiyasa.gui.AdminGuiActions;
import com.mcpiyasa.gui.AdminGuiHost;
import com.mcpiyasa.gui.CategoryMenu;
import com.mcpiyasa.gui.Change24h;
import com.mcpiyasa.gui.GuiListener;
import com.mcpiyasa.gui.MainMenu;
import com.mcpiyasa.gui.MenuHolder;
import com.mcpiyasa.market.BukkitInventory;
import com.mcpiyasa.market.Clock;
import com.mcpiyasa.market.EconomyBridge;
import com.mcpiyasa.market.MarketService;
import com.mcpiyasa.market.SignalTracker;
import com.mcpiyasa.market.SignalTrackerSnapshot;
import com.mcpiyasa.market.VaultEconomy;
import com.mcpiyasa.storage.AsyncWriter;
import com.mcpiyasa.storage.Db;
import com.mcpiyasa.storage.EngineMetaRepo;
import com.mcpiyasa.storage.ProfileRepo;
import com.mcpiyasa.storage.SnapshotRepo;
import com.mcpiyasa.storage.StateRepo;
import com.mcpiyasa.storage.StorageFailureHandler;
import com.mcpiyasa.storage.TradePersistenceRepo;
import com.mcpiyasa.storage.TxLogRepo;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/** MCPiyasa modullerini Bukkit yasam dongusune baglar. */
public final class MCPiyasaPlugin extends JavaPlugin
        implements Listener, AdminGuiHost {
    private static final String DATABASE_FILE_NAME = "mcpiyasa.db";
    private static final String ITEMS_FILE_NAME = "items.yml";
    /** Paketle birlikte gelen mesaj dilleri; ilk acilista data klasorune kopyalanir. */
    private static final String[] MESAJ_DILLERI = {
        "tr", "en", "de", "fr", "es", "pt", "ru", "pl", "it", "zh"
    };
    private static final long MINUTE_TICKS = 1200L;
    private static final long AUTOSAVE_TICKS = 5L * 60L * 20L;
    private static final long PAPI_CACHE_TICKS = 100L;
    private static final double DAILY_EMA_ALPHA = 0.3;

    private final Map<String, Double> dailyVolumeEma =
        new LinkedHashMap<String, Double>();
    private final ApiFacade apiFacade = new ApiFacade();

    private SafeMode safeMode;
    private PluginSettings settings;
    private Messages messages;
    private ItemNames itemNames;
    private ParsedItems parsedItems;
    private Clock clock;

    private Db db;
    private StateRepo stateRepo;
    private EngineMetaRepo engineMetaRepo;
    private ProfileRepo profileRepo;
    private SnapshotRepo snapshotRepo;
    private TxLogRepo txLogRepo;
    private TradePersistenceRepo tradePersistenceRepo;
    private StorageFailureHandler storageFailureHandler;
    private AsyncWriter asyncWriter;

    private PriceEngine engine;
    private SignalTracker signalTracker;
    private RuntimeServices activeServices;
    private boolean tasksScheduled;
    private boolean papiCacheFailureLogged;
    private boolean npcRegistered;
    private boolean apiFacadeRegistered;
    private String lastMinuteDayKey;

    @Override
    public void onEnable() {
        safeMode = new SafeMode();
        clock = new SystemClock();
        registerJoinNoticeListenerSafely();
        try {
            registerApiFacadeOnce();
            prepareResources();
            Definitions definitions = loadDefinitions(safeMode);
            applyDefinitions(definitions);
            initializeStorage();
            RuntimeServices services = buildServices(definitions, safeMode, true);
            applyRuntime(services);
            activeServices = services;
            bindRuntime(services);
            registerNpcIntegration();
            ensureTasksScheduled();
            logLoadedDefinitions();
            apiFacade.swap(services.api);
            getLogger().info("MCPiyasa etkin.");
            getLogger().info(
                "Gunluk hacim EMA verisi yeniden baslatmada geri yuklenmez; "
                    + "ilk gun kapanisi yeni tohumu olusturur.");
        } catch (Throwable failure) {
            apiFacade.unavailable();
            cancelAllSchedulerTasks();
            try {
                clearRuntimeBindings(activeServices);
            } catch (RuntimeException | LinkageError cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
                getLogger().log(
                    Level.SEVERE,
                    "Basarisiz acilis kayitlari temizlenemedi",
                    cleanupFailure);
            }
            activateSafeMode("MCPiyasa acilisi tamamlanamadi", failure);
        }
    }

    /**
     * Once yeni is uretimini durdurur ve bekleyen asenkron yazilari bosaltir.
     * Son senkron snapshot bundan sonra yazilir; kuyruktaki eski bir kayit en
     * taze kapanis durumunun ustune yazamaz. DB en son kapatilir.
     */
    @Override
    public void onDisable() {
        // GuiListener kapaninca acik menudeki isimli ikonlar (NETHER_STAR,
        // BARRIER, gercek cevherler) oyuncuda serbest kalir; once menuleri
        // kapatiyoruz.
        closeOpenMarketMenusSafely();
        apiFacade.unavailable();
        cancelAllSchedulerTasks();
        if (asyncWriter != null) {
            boolean flushed = false;
            try {
                flushed = asyncWriter.closeAndFlush(5000L);
            } catch (RuntimeException failure) {
                getLogger().log(
                    Level.WARNING,
                    "Asenkron yazici kapatilirken hata olustu; yazilar kaybolmus olabilir.",
                    failure);
            }
            if (!flushed) {
                getLogger().warning(
                    "Asenkron yazici 5 saniyede bosalmadi; yazilar kaybolmus olabilir.");
            }
        }

        saveStatesSynchronously();
        saveProfilesSynchronously();

        if (db != null) {
            db.close();
        }
        if (apiFacadeRegistered) {
            getServer().getServicesManager().unregisterAll(this);
            apiFacadeRegistered = false;
        }
    }

    private void closeOpenMarketMenusSafely() {
        try {
            closeOpenMarketMenus(getServer().getOnlinePlayers(), getLogger());
        } catch (RuntimeException | LinkageError failure) {
            getLogger().log(
                Level.WARNING,
                "Acik market menuleri kapatilamadi",
                failure);
        }
    }

    /**
     * MenuHolder tasiyan acik envanterleri kapatir. Kapali eklentide bu
     * ikonlar iptal edilemedigi icin oyuncunun envanterine gecebilir.
     *
     * @return kapatilan menu sayisi
     */
    static int closeOpenMarketMenus(
            Iterable<? extends Player> players, Logger logger) {
        if (players == null) {
            return 0;
        }
        int closed = 0;
        for (Player player : players) {
            if (player == null) {
                continue;
            }
            try {
                if (player.getOpenInventory() == null
                        || player.getOpenInventory().getTopInventory() == null
                        || !(player.getOpenInventory().getTopInventory()
                            .getHolder() instanceof MenuHolder)) {
                    continue;
                }
                player.closeInventory();
                closed++;
            } catch (RuntimeException | LinkageError failure) {
                if (logger != null) {
                    logger.log(
                        Level.WARNING,
                        "Acik market menusu kapatilamadi player="
                            + player.getUniqueId(),
                        failure);
                }
            }
        }
        return closed;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (safeMode != null && safeMode.isActive()
                && event.getPlayer().hasPermission("mcpiyasa.admin")
                && messages != null) {
            event.getPlayer().sendMessage(safeModeNotice(safeMode));
        }
    }

    // --- AdminGuiHost: admin menulerine her zaman canli runtime verisi ---

    @Override
    public ParsedItems items() {
        RuntimeServices services = activeServices;
        return services == null ? parsedItems : services.definitions.parsedItems;
    }

    @Override
    public PluginSettings settings() {
        RuntimeServices services = activeServices;
        return services == null ? settings : services.definitions.settings;
    }

    @Override
    public Messages messages() {
        RuntimeServices services = activeServices;
        return services == null ? messages : services.definitions.messages;
    }

    @Override
    public ItemNames itemNames() {
        RuntimeServices services = activeServices;
        return services == null ? itemNames : services.definitions.itemNames;
    }

    @Override
    public MarketService marketService() {
        RuntimeServices services = activeServices;
        return services == null ? null : services.marketService;
    }

    @Override
    public SafeMode safeMode() {
        RuntimeServices services = activeServices;
        return services == null ? safeMode : services.safeMode;
    }

    @Override
    public AdminGuiActions actions() {
        RuntimeServices services = activeServices;
        return services == null ? null : services.adminCommands;
    }

    private void prepareResources() {
        try {
            saveDefaultConfig();
        } catch (RuntimeException failure) {
            activateSafeMode("Varsayilan config.yml kaydedilemedi", failure);
        }
        saveResourceIfAbsent(ITEMS_FILE_NAME);
        for (String dil : MESAJ_DILLERI) {
            saveResourceIfAbsent("messages_" + dil + ".yml");
            saveResourceIfAbsent("item-names_" + dil + ".yml");
        }
    }

    private void saveResourceIfAbsent(String resourceName) {
        File target = new File(getDataFolder(), resourceName);
        if (target.exists()) {
            return;
        }
        try {
            saveResource(resourceName, false);
        } catch (RuntimeException failure) {
            activateSafeMode(resourceName + " kaydedilemedi", failure);
        }
    }

    /** Config, mesaj ve item tanimlarini alanlara dokunmadan yukler. */
    private Definitions loadDefinitions(SafeMode targetSafeMode) {
        boolean successful = true;
        PluginSettings loadedSettings;
        Messages loadedMessages;
        ItemNames loadedItemNames;
        ParsedItems loadedItems;

        try {
            loadedSettings = ConfigLoader.load(
                loadYaml(new File(getDataFolder(), "config.yml")));
        } catch (IOException | InvalidConfigurationException | RuntimeException failure) {
            successful = false;
            activateSafeMode(
                targetSafeMode,
                "config.yml yuklenemedi; varsayilan ayarlar kullaniliyor",
                failure);
            loadedSettings = loadDefaultSettings(targetSafeMode);
        }

        String language = loadedSettings.dil;
        if (!isSafeLanguage(language)) {
            successful = false;
            activateSafeMode(
                targetSafeMode, "Gecersiz dil kimligi; tr kullaniliyor", null);
            language = "tr";
        }
        try {
            loadedMessages = loadMessages(new File(
                getDataFolder(), "messages_" + language + ".yml"));
        } catch (IOException | InvalidConfigurationException | RuntimeException failure) {
            successful = false;
            activateSafeMode(
                targetSafeMode,
                "messages_" + language + ".yml yuklenemedi; paket mesaji kullaniliyor",
                failure);
            loadedMessages = loadDefaultMessages(language, targetSafeMode);
        }

        try {
            loadedItemNames = loadItemNames(new File(
                getDataFolder(), "item-names_" + language + ".yml"));
        } catch (IOException | InvalidConfigurationException | RuntimeException failure) {
            successful = false;
            activateSafeMode(
                targetSafeMode,
                "item-names_" + language
                    + ".yml yuklenemedi; paket urun adlari kullaniliyor",
                failure);
            loadedItemNames = loadDefaultItemNames(language, targetSafeMode);
        }

        try {
            loadedItems = ItemsLoader.load(
                loadYaml(new File(getDataFolder(), ITEMS_FILE_NAME)),
                loadedSettings.varsayilanTabanStok,
                loadedSettings.hassasiyet,
                loadedSettings.maxTabanFiyat);
            for (String notice : loadedItems.notices) {
                getLogger().info("items.yml: " + notice);
            }
            if (!loadedItems.diagnostics.isEmpty()) {
                successful = false;
                String detail = loadedItems.diagnostics.get(0);
                targetSafeMode.activate("diag.items-yapilandirma", detail);
                for (String diagnostic : loadedItems.diagnostics) {
                    getLogger().severe(
                        "items.yml loader diagnostics: " + diagnostic);
                }
            }
        } catch (IOException | InvalidConfigurationException | RuntimeException failure) {
            successful = false;
            activateSafeMode(
                targetSafeMode,
                "items.yml yuklenemedi; paket tanimlari kullaniliyor",
                failure);
            loadedItems = loadDefaultItems(loadedSettings, targetSafeMode);
        }
        return new Definitions(
            loadedSettings, loadedMessages, loadedItemNames, loadedItems,
            successful);
    }

    /** Canli alanlara veya mevcut kayitlara dokunmadan yeni runtime'i kurar. */
    private RuntimeServices buildServices(
            Definitions definitions,
            final SafeMode targetSafeMode,
            boolean restorePersistentState) {
        if (getCommand("market") == null) {
            throw new IllegalStateException("plugin.yml market komutu bulunamadi");
        }
        PriceEngine candidateEngine = new PriceEngine(
            definitions.settings.engineParams,
            definitions.parsedItems.groups,
            definitions.parsedItems.items);
        if (restorePersistentState) {
            restoreEngineStates(
                candidateEngine, targetSafeMode, definitions.settings.hassasiyet);
        }

        SignalTracker candidateSignalTracker = new SignalTracker(
            definitions.settings.profilAlpha,
            definitions.settings.profilWarmup);
        if (restorePersistentState) {
            restoreProfiles(
                candidateSignalTracker, definitions.parsedItems, targetSafeMode);
        }
        runDiagnostics(definitions, targetSafeMode);

        MarketService candidateMarketService = new MarketService(
            candidateEngine,
            definitions.parsedItems,
            definitions.settings,
            candidateSignalTracker,
            targetSafeMode,
            resolveEconomyBridge(),
            new BukkitInventory(getLogger()),
            clock,
            new EventTradePreHook(),
            asyncWriter,
            txLogRepo,
            tradePersistenceRepo,
            getLogger(),
            new Runnable() {
                @Override
                public void run() {
                    notifySafeModeAdmins(targetSafeMode);
                }
            },
            definitions.itemNames);
        Change24h change24h = new Change24h(
            candidateEngine, snapshotRepo, clock);
        ApiImpl api = new ApiImpl(
            candidateMarketService,
            candidateEngine,
            definitions.parsedItems,
            definitions.settings.engineParams,
            candidateSignalTracker,
            snapshotRepo,
            txLogRepo,
            clock);

        PapiHook papiHook = null;
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                papiHook = PapiHook.create(
                    this,
                    definitions.settings.engineParams.sigma,
                    candidateEngine,
                    definitions.parsedItems,
                    change24h);
            } catch (RuntimeException | LinkageError failure) {
                getLogger().log(
                    Level.WARNING,
                    "PlaceholderAPI entegrasyonu hazirlanamadi; devre disi birakildi.",
                    failure);
            }
        }

        AdminCommands adminCommands = createAdminCommands(
            definitions, targetSafeMode, candidateEngine);
        MarketCommand marketCommand = new MarketCommand(
            definitions.parsedItems,
            definitions.settings,
            definitions.messages,
            definitions.itemNames,
            candidateEngine,
            candidateMarketService);
        marketCommand.setAdminDelegate(adminCommands);
        marketCommand.setAdminGuiHost(this);
        Listener guiListener = new GuiListener(
            definitions.parsedItems,
            definitions.settings,
            definitions.messages,
            definitions.itemNames,
            candidateEngine,
            candidateMarketService,
            change24h,
            new GuiListener.MenuScheduler() {
                @Override
                public void open(Runnable openTask) {
                    openMenuNextTick(openTask);
                }
            },
            this);
        Listener signShopListener = definitions.settings.tabelaMarket
            ? new SignShopListener(
                definitions.parsedItems,
                definitions.settings,
                definitions.messages,
                definitions.itemNames,
                candidateMarketService,
                change24h)
            : null;

        return new RuntimeServices(
            definitions,
            targetSafeMode,
            candidateEngine,
            candidateSignalTracker,
            candidateMarketService,
            change24h,
            api,
            marketCommand,
            adminCommands,
            guiListener,
            signShopListener,
            papiHook);
    }

    /**
     * Bukkit, InventoryClickEvent islenirken envanter acmayi desteklemez;
     * iptal sonrasi gonderilen slot guncellemeleri yeni container'a yazilip
     * hayalet item gorunumu uretebilir. Menu acmayi bir tik erteliyoruz.
     */
    private void openMenuNextTick(final Runnable openTask) {
        if (openTask == null) {
            return;
        }
        try {
            SchedulerCompat.runLater(this, openTask, 1L);
        } catch (RuntimeException | LinkageError failure) {
            getLogger().log(
                Level.WARNING,
                "Menu acilisi ertelenemedi; dogrudan aciliyor",
                failure);
            openTask.run();
        }
    }

    private void initializeStorage() {
        File databaseFile = new File(getDataFolder(), DATABASE_FILE_NAME);
        db = new Db(databaseFile, getLogger());
        stateRepo = new StateRepo(db);
        engineMetaRepo = new EngineMetaRepo(db);
        profileRepo = new ProfileRepo(db, getLogger());
        snapshotRepo = new SnapshotRepo(db);
        txLogRepo = new TxLogRepo(db);
        tradePersistenceRepo = new TradePersistenceRepo(
            db, txLogRepo, stateRepo);
        storageFailureHandler = new StorageFailureHandler(
            safeMode,
            new StorageFailureHandler.MainThreadDispatcher() {
                @Override
                public void dispatch(Runnable notification) {
                    SchedulerCompat.runSync(
                        MCPiyasaPlugin.this, notification);
                }
            },
            new Runnable() {
                @Override
                public void run() {
                    notifyStorageFailureAdmins();
                }
            });
        asyncWriter = new AsyncWriter(getLogger(), storageFailureHandler);
    }

    private void restoreEngineStates(PriceEngine targetEngine,
                                     SafeMode targetSafeMode,
                                     double currentHassasiyet) {
        try {
            restoreEngineStates(
                targetEngine, stateRepo, engineMetaRepo, currentHassasiyet,
                getLogger());
        } catch (RuntimeException failure) {
            activateSafeMode(
                targetSafeMode, "Grup durumlari geri yuklenemedi", failure);
        }
    }

    /**
     * Kalici stogu, yazildigi anki hassasiyet ile mevcut hassasiyet
     * farkliysa fiyati koruyacak sekilde yeniden olcekleyerek yukler. Kayitli
     * hassasiyet yoksa (temiz DB / yukseltme) mevcut deger baseline alinir
     * (olcekleme yapilmaz); ardindan mevcut deger bir sonraki calisma icin
     * kalicilastirilir. Bukkit'ten bagimsiz, dogrudan test edilebilir.
     */
    static void restoreEngineStates(PriceEngine targetEngine,
                                    StateRepo stateRepo,
                                    EngineMetaRepo engineMetaRepo,
                                    double currentHassasiyet,
                                    Logger logger) {
        if (stateRepo == null || engineMetaRepo == null) {
            throw new IllegalArgumentException(
                "Engine state restore bagimliliklari null olamaz");
        }
        Map<String, double[]> storedStates = stateRepo.loadAll();
        Double storedHassasiyet = engineMetaRepo.loadHassasiyet();
        double hassasiyetOld = storedHassasiyet == null
            ? currentHassasiyet : storedHassasiyet.doubleValue();
        restoreEngineStates(
            targetEngine, storedStates, hassasiyetOld, currentHassasiyet,
            logger);
        engineMetaRepo.saveHassasiyet(currentHassasiyet);
    }

    /** Geriye donuk uyumlu: olcekleme uygulamadan geri yukler. */
    static void restoreEngineStates(PriceEngine targetEngine,
                                    Map<String, double[]> storedStates,
                                    Logger logger) {
        restoreEngineStates(targetEngine, storedStates, 1.0, 1.0, logger);
    }

    /**
     * Stok, {@code hassasiyetOld/hassasiyetNew} ile yeniden olceklenerek
     * yuklenir; bu {@code baseStock/stock} oranini (dolayisiyla fiyati)
     * hassasiyet degisiminden korur. Iki deger de sonlu&pozitif degilse
     * (bozuk/eksik meta) olcekleme uygulanmaz.
     */
    static void restoreEngineStates(PriceEngine targetEngine,
                                    Map<String, double[]> storedStates,
                                    double hassasiyetOld,
                                    double hassasiyetNew,
                                    Logger logger) {
        if (targetEngine == null || storedStates == null || logger == null) {
            throw new IllegalArgumentException(
                "Engine state restore bagimliliklari null olamaz");
        }
        double scale = Double.isFinite(hassasiyetOld) && hassasiyetOld > 0.0
                && Double.isFinite(hassasiyetNew) && hassasiyetNew > 0.0
            ? hassasiyetOld / hassasiyetNew : 1.0;
        for (Map.Entry<String, double[]> entry : storedStates.entrySet()) {
            double[] state = entry.getValue();
            if (state == null || state.length < 2) {
                logger.warning(
                    "Eksik grup durumu geri yuklenmedi"
                        + " groupId=" + entry.getKey());
                continue;
            }
            double stock = state[0];
            double epsilon = state[1];
            if (Double.isFinite(stock) && stock > 0.0
                    && Double.isFinite(epsilon) && epsilon > 0.0) {
                double rescaledStock = stock * scale;
                if (!Double.isFinite(rescaledStock) || rescaledStock <= 0.0) {
                    logger.warning(
                        "Hassasiyet olceklemesi gecersiz sonuc uretti;"
                            + " olceksiz geri yuklendi groupId="
                            + entry.getKey());
                    rescaledStock = stock;
                }
                targetEngine.restoreState(
                    entry.getKey(), rescaledStock, epsilon);
            } else {
                logger.warning(
                    "Gecersiz grup durumu geri yuklenmedi"
                        + " groupId=" + entry.getKey()
                        + " stock=" + stock
                        + " epsilon=" + epsilon);
            }
        }
    }

    private void restoreProfiles(SignalTracker targetSignalTracker,
                                 ParsedItems targetItems,
                                 SafeMode targetSafeMode) {
        try {
            for (String groupId : targetItems.groups.keySet()) {
                VolumeProfile profile = targetSignalTracker.profile(groupId);
                double[] ema = profileRepo.loadEma(groupId);
                int[] count = profileRepo.loadCount(groupId);
                if (ema != null && count != null) {
                    profile.restore(ema, count);
                } else if (ema != null || count != null) {
                    getLogger().warning(
                        "Eksik hacim profili geri yuklenmedi: " + groupId);
                }
            }
        } catch (RuntimeException failure) {
            activateSafeMode(
                targetSafeMode, "Hacim profilleri geri yuklenemedi", failure);
        }
    }

    private void runDiagnostics(
            Definitions definitions, SafeMode targetSafeMode) {
        List<String> bypassedChecks = new ArrayList<String>();
        if (targetSafeMode.isActive() && !targetSafeMode.reason().isEmpty()) {
            bypassedChecks.add(targetSafeMode.reason());
        }
        List<DiagCheck> checks = Diagnostics.runAll(
            this,
            new File(getDataFolder(), DATABASE_FILE_NAME),
            definitions.parsedItems.items.size(),
            definitions.messages::get);
        for (DiagCheck check : checks) {
            if (!check.ok) {
                bypassedChecks.add(check.id);
                String detail = definitions.messages.get(check.messageKey);
                targetSafeMode.activate(
                    check.messageKey,
                    detail);
                getLogger().severe(
                    "Diagnostics basarisiz code=" + check.messageKey
                        + " detail=" + detail);
            }
        }
        getLogger().info(
            "guvenli-mod.zorla-calistir=" + definitions.settings.zorlaCalistir);
        if (definitions.settings.zorlaCalistir && !bypassedChecks.isEmpty()) {
            getLogger().warning(definitions.messages.get(
                "diag.zorla-calisiyor", Collections.singletonMap(
                    "kontroller", bypassedChecks.toString())));
        }
    }

    private EconomyBridge resolveEconomyBridge() {
        RegisteredServiceProvider<Economy> registration =
            getServer().getServicesManager().getRegistration(Economy.class);
        Economy provider = registration == null ? null : registration.getProvider();
        if (provider != null) {
            return new VaultEconomy(provider);
        }
        return UnavailableEconomy.INSTANCE;
    }

    private void registerNpcIntegration() {
        if (settings.npcMarket && !npcRegistered) {
            try {
                npcRegistered = NpcHook.tryRegister(this, new NpcHook.MainMenuOpener() {
                    @Override
                    public void open(final Player player) {
                        openNpcMenu(
                            player, MCPiyasaPlugin.this.messages,
                            new Runnable() {
                                @Override public void run() {
                                    MainMenu.open(
                                        player,
                                        MCPiyasaPlugin.this.parsedItems,
                                        MCPiyasaPlugin.this.settings,
                                        MCPiyasaPlugin.this.messages);
                                }
                            });
                    }
                });
            } catch (RuntimeException | LinkageError failure) {
                getLogger().log(
                    Level.WARNING, "Citizens NPC marketi kaydedilemedi.", failure);
            }
        }
    }

    static boolean openNpcMenu(Player player, Messages activeMessages,
                               Runnable opener) {
        if (player == null || activeMessages == null || opener == null) {
            throw new IllegalArgumentException(
                "NPC menu bagimliliklari null olamaz");
        }
        if (!player.hasPermission("mcpiyasa.use")) {
            player.sendMessage(activeMessages.chat("komut.yetki-yok"));
            return false;
        }
        opener.run();
        return true;
    }

    private AdminCommands createAdminCommands(
            Definitions definitions,
            SafeMode targetSafeMode,
            PriceEngine targetEngine) {
        return new AdminCommands(
            definitions.parsedItems,
            definitions.messages,
            targetEngine,
            definitions.settings.maxTabanFiyat,
            targetSafeMode,
            new File(getDataFolder(), ITEMS_FILE_NAME),
            new AdminCommands.Reloader() {
                @Override
                public ReloadResult reload() {
                    return reloadServices();
                }

                @Override
                public Messages activeMessages() {
                    return MCPiyasaPlugin.this.messages;
                }
            },
            stateRepo,
            storageFailureHandler,
            asyncWriter,
            getLogger());
    }

    private void bindRuntime(RuntimeServices services) {
        PluginCommand command = getCommand("market");
        if (command == null) {
            throw new IllegalStateException("plugin.yml market komutu bulunamadi");
        }
        command.setExecutor(services.marketCommand);
        command.setTabCompleter(services.marketCommand);

        getServer().getPluginManager().registerEvents(
            services.guiListener, this);
        if (services.signShopListener != null) {
            getServer().getPluginManager().registerEvents(
                services.signShopListener, this);
        }
        if (services.papiHook != null) {
            try {
                if (!services.papiHook.register()) {
                    getLogger().warning(
                        "PlaceholderAPI genisletmesi kaydedilemedi; entegrasyon devre disi birakildi.");
                    services.papiHook = null;
                }
            } catch (RuntimeException | LinkageError failure) {
                getLogger().log(
                    Level.WARNING,
                    "PlaceholderAPI genisletmesi kaydedilemedi; entegrasyon devre disi birakildi.",
                    failure);
                services.papiHook = null;
            }
        }
    }

    private void registerApiFacadeOnce() {
        if (apiFacadeRegistered) {
            return;
        }
        getServer().getServicesManager().register(
            MCPiyasaAPI.class,
            apiFacade,
            this,
            ServicePriority.Normal);
        apiFacadeRegistered = true;
    }

    private ReloadResult reloadServices() {
        if (Bukkit.getServer() != null && !Bukkit.isPrimaryThread()) {
            getLogger().severe(
                "Reload reddedildi: snapshot ve runtime swap main thread gerektirir");
            return ReloadResult.FAILED_CANDIDATE;
        }
        RuntimeServices previous = activeServices;
        if (previous == null) {
            activateSafeMode(
                "reload-no-live-runtime",
                "Canli runtime bulunamadi; sunucuyu yeniden baslatin.",
                "MCPiyasa yeniden yuklenemedi; canli servis yok",
                null);
            return ReloadResult.FAILED_ROLLBACK;
        }
        EngineSnapshot engineSnapshot = previous.engine.snapshot();
        SignalTrackerSnapshot signalSnapshot =
            previous.signalTracker.snapshot();
        SafeMode candidateSafeMode = new SafeMode();
        Definitions definitions;
        try {
            definitions = loadDefinitions(candidateSafeMode);
            if (!definitions.successful) {
                getLogger().warning(
                    "Reload candidate reddedildi: config/mesaj/item tanimlari eksik");
                return ReloadResult.FAILED_CANDIDATE;
            }
        } catch (RuntimeException | LinkageError failure) {
            getLogger().log(
                Level.SEVERE,
                "Reload candidate tanimlari kurulurken hata olustu; canli runtime korundu",
                failure);
            return ReloadResult.FAILED_CANDIDATE;
        }
        RuntimeServices candidate;
        try {
            candidate = buildServices(
                definitions, candidateSafeMode, false);
            // Hassasiyet reload'da degistiyse, alinan snapshot'in stogu
            // ESKI hassasiyete gore olceklidir; YENI baseStock'a sahip aday
            // motora aktarilirken hassasiyet_eski/hassasiyet_yeni ile
            // yeniden olceklenir, boylece fiyat (baseStock/stock orani)
            // hassasiyet degisiminden etkilenmez.
            double hassasiyetOld = previous.definitions.settings.hassasiyet;
            double hassasiyetNew = definitions.settings.hassasiyet;
            double stockScale =
                Double.isFinite(hassasiyetOld) && hassasiyetOld > 0.0
                        && Double.isFinite(hassasiyetNew) && hassasiyetNew > 0.0
                    ? hassasiyetOld / hassasiyetNew : 1.0;
            candidate.engine.restoreSnapshot(engineSnapshot, stockScale);
            candidate.signalTracker.restoreSnapshot(
                signalSnapshot,
                candidate.definitions.parsedItems.groups.keySet());
        } catch (RuntimeException | LinkageError failure) {
            getLogger().log(
                Level.SEVERE,
                "Reload candidate runtime'i kurulurken hata olustu; canli runtime korundu",
                failure);
            return ReloadResult.FAILED_CANDIDATE;
        }
        return replaceRuntime(previous, candidate);
    }

    private ReloadResult replaceRuntime(
            RuntimeServices previous, RuntimeServices candidate) {
        boolean timersReplaced = false;
        try {
            clearRuntimeBindings(previous);
            bindRuntime(candidate);
            applyRuntime(candidate);

            timersReplaced = true;
            cancelSchedulerTasksStrict();
            ensureTasksScheduled();

            lastMinuteDayKey = null;
            dailyVolumeEma.keySet().retainAll(parsedItems.groups.keySet());
            registerNpcIntegration();
            logLoadedDefinitions();
            boolean candidateSafe = candidate.safeMode.isActive();
            if (candidateSafe) {
                notifySafeModeAdmins(candidate.safeMode);
            }
            activeServices = candidate;
            apiFacade.swap(candidate.api);
            return candidateSafe
                ? ReloadResult.OK_SAFE_MODE : ReloadResult.OK;
        } catch (RuntimeException | LinkageError failure) {
            getLogger().log(
                Level.SEVERE,
                "Reload candidate bind/swap basarisiz oldu; rollback deneniyor",
                failure);
            if (rollbackRuntime(
                    previous, candidate, timersReplaced, failure)) {
                getLogger().warning(
                    "Reload candidate reddedildi; onceki runtime geri yuklendi");
                return ReloadResult.FAILED_CANDIDATE;
            }
            boolean wasAlreadySafe = previous.safeMode.isActive();
            boolean previousIsApplied = safeMode == previous.safeMode;
            activateSafeMode(
                previous.safeMode,
                "reload-rollback-failed",
                "Reload rollback tamamlanamadi; sunucuyu yeniden baslatin ve konsolu inceleyin.",
                "Reload rollback basarisiz; canli runtime guvenli moda alindi",
                failure);
            if (wasAlreadySafe || !previousIsApplied) {
                notifySafeModeAdmins(previous.safeMode);
            }
            return ReloadResult.FAILED_ROLLBACK;
        }
    }

    private boolean rollbackRuntime(RuntimeServices previous,
                                    RuntimeServices candidate,
                                    boolean timersReplaced,
                                    Throwable originalFailure) {
        boolean successful = true;
        try {
            clearRuntimeBindings(candidate);
        } catch (RuntimeException | LinkageError rollbackFailure) {
            successful = false;
            originalFailure.addSuppressed(rollbackFailure);
            getLogger().log(
                Level.SEVERE,
                "Basarisiz yeni runtime kayitlari temizlenemedi",
                rollbackFailure);
        }

        try {
            applyRuntime(previous);
            activeServices = previous;
        } catch (RuntimeException | LinkageError rollbackFailure) {
            successful = false;
            originalFailure.addSuppressed(rollbackFailure);
            getLogger().log(
                Level.SEVERE,
                "Onceki runtime alanlari geri yuklenemedi",
                rollbackFailure);
        }
        try {
            bindRuntime(previous);
        } catch (RuntimeException | LinkageError rollbackFailure) {
            successful = false;
            originalFailure.addSuppressed(rollbackFailure);
            getLogger().log(
                Level.SEVERE,
                "Onceki runtime kayitlari geri yuklenemedi",
                rollbackFailure);
        }

        if (timersReplaced) {
            cancelAllSchedulerTasks();
            try {
                ensureTasksScheduled();
            } catch (RuntimeException | LinkageError timerFailure) {
                successful = false;
                originalFailure.addSuppressed(timerFailure);
                getLogger().log(
                    Level.SEVERE,
                    "Onceki runtime zamanlayicilari geri yuklenemedi",
                    timerFailure);
            }
        }
        if (!successful) {
            // Dinleyici kaydi belirsiz kaldi: acik menudeki isimli ikonlarin
            // oyuncuda kalma riskini kapatiyoruz.
            closeOpenMarketMenusSafely();
        }
        return successful;
    }

    private void applyDefinitions(Definitions definitions) {
        settings = definitions.settings;
        messages = definitions.messages;
        itemNames = definitions.itemNames;
        parsedItems = definitions.parsedItems;
    }

    private void applyRuntime(RuntimeServices services) {
        applyDefinitions(services.definitions);
        safeMode = services.safeMode;
        engine = services.engine;
        signalTracker = services.signalTracker;
        if (storageFailureHandler != null) {
            storageFailureHandler.setSafeMode(services.safeMode);
        }
        papiCacheFailureLogged = false;
    }

    private void notifyStorageFailureAdmins() {
        notifySafeModeAdmins(safeMode);
    }

    private void notifySafeModeAdmins(SafeMode targetSafeMode) {
        if (targetSafeMode == null || !targetSafeMode.isActive()) {
            return;
        }
        try {
            String notice = safeModeNotice(targetSafeMode);
            for (Player player : getServer().getOnlinePlayers()) {
                if (player.hasPermission("mcpiyasa.admin")) {
                    try {
                        player.sendMessage(notice);
                    } catch (RuntimeException | LinkageError playerFailure) {
                        getLogger().log(
                            Level.WARNING,
                            "Safe mode bildirimi admin oyuncuya gonderilemedi",
                            playerFailure);
                    }
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            getLogger().log(
                Level.WARNING,
                "Safe mode admin bildirimleri hazirlanamadi",
                failure);
        }
    }

    private String safeModeNotice(SafeMode targetSafeMode) {
        if (messages == null) {
            return "MCPiyasa safe mode active: "
                + targetSafeMode.reason() + " - " + targetSafeMode.detail();
        }
        Map<String, String> vars = new LinkedHashMap<String, String>();
        vars.put("kod", targetSafeMode.reason());
        vars.put("detay", targetSafeMode.detail());
        return messages.get("admin.safe-mode-alert", vars);
    }

    private void clearRuntimeBindings(RuntimeServices services) {
        clearCommandBindings();
        if (services != null) {
            HandlerList.unregisterAll(services.guiListener);
            if (services.signShopListener != null) {
                HandlerList.unregisterAll(services.signShopListener);
            }
            unregisterPapiHook(services.papiHook);
        }
    }

    private void clearCommandBindings() {
        PluginCommand command = getCommand("market");
        if (command != null) {
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
    }

    private void unregisterPapiHook(PapiHook hook) {
        if (hook == null) {
            return;
        }
        try {
            hook.unregister();
        } catch (RuntimeException | LinkageError failure) {
            getLogger().log(
                Level.WARNING,
                "Eski PlaceholderAPI genisletmesi kaldirilamadi.",
                failure);
        }
    }

    private void registerJoinNoticeListenerSafely() {
        try {
            getServer().getPluginManager().registerEvents(this, this);
        } catch (RuntimeException | LinkageError failure) {
            activateSafeMode(
                "Guvenli mod katilim bildirimi kaydedilemedi", failure);
        }
    }

    private void ensureTasksScheduled() {
        if (tasksScheduled) {
            return;
        }
        SchedulerCompat.repeatSync(this, new Runnable() {
            @Override
            public void run() {
                runMinuteTick();
            }
        }, MINUTE_TICKS, MINUTE_TICKS);

        long reversionTicks = settings.tickDakika * MINUTE_TICKS;
        SchedulerCompat.repeatSync(this, new Runnable() {
            @Override
            public void run() {
                try {
                    if (engine != null) {
                        engine.reversionTick();
                    }
                } catch (RuntimeException failure) {
                    getLogger().log(Level.SEVERE, "Toparlanma tiki basarisiz oldu", failure);
                }
            }
        }, reversionTicks, reversionTicks);

        SchedulerCompat.repeatSync(this, new Runnable() {
            @Override
            public void run() {
                queueStateAutosave();
            }
        }, AUTOSAVE_TICKS, AUTOSAVE_TICKS);

        if (activeServices != null && activeServices.papiHook != null) {
            SchedulerCompat.repeatSync(this, new Runnable() {
                @Override
                public void run() {
                    refreshPapiPriceCache();
                }
            }, PAPI_CACHE_TICKS, PAPI_CACHE_TICKS);
        }
        tasksScheduled = true;
    }

    private void refreshPapiPriceCache() {
        RuntimeServices services = activeServices;
        if (services == null || services.papiHook == null) {
            return;
        }
        try {
            services.papiHook.refresh(
                services.engine,
                services.definitions.parsedItems,
                services.change24h);
            papiCacheFailureLogged = false;
        } catch (RuntimeException | LinkageError failure) {
            if (!papiCacheFailureLogged) {
                papiCacheFailureLogged = true;
                getLogger().log(
                    Level.SEVERE,
                    "PlaceholderAPI fiyat onbellegi yenilenemedi; tekrarlar basariya kadar bastirilacak",
                    failure);
            }
        }
    }

    private void runMinuteTick() {
        if (signalTracker == null || engine == null || parsedItems == null) {
            return;
        }
        try {
            String currentDayKey = clock.dayKey();
            String closedDayKey = lastMinuteDayKey;
            Map<String, Double> closedDayVolumes = signalTracker.minuteTick(
                clock.slotIndex(), currentDayKey);
            lastMinuteDayKey = currentDayKey;
            if (!closedDayVolumes.isEmpty()) {
                closeMarketDay(closedDayKey, closedDayVolumes);
            }
        } catch (RuntimeException failure) {
            getLogger().log(Level.SEVERE, "Dakika tiki basarisiz oldu", failure);
        }
    }

    private void closeMarketDay(
            String closedDayKey, Map<String, Double> closedDayVolumes) {
        for (Map.Entry<String, Double> entry : closedDayVolumes.entrySet()) {
            double closedVolume = entry.getValue() == null
                ? 0.0 : entry.getValue().doubleValue();
            Double previous = dailyVolumeEma.get(entry.getKey());
            double updated = previous == null
                ? closedVolume
                : DAILY_EMA_ALPHA * closedVolume
                    + (1.0 - DAILY_EMA_ALPHA) * previous.doubleValue();
            dailyVolumeEma.put(entry.getKey(), Double.valueOf(updated));
        }
        dailyVolumeEma.keySet().retainAll(parsedItems.groups.keySet());

        Map<String, Double> calibrationInput =
            new LinkedHashMap<String, Double>(dailyVolumeEma);
        Map<String, double[]> changes = engine.recalibrate(calibrationInput);
        for (Map.Entry<String, double[]> entry : changes.entrySet()) {
            double[] change = entry.getValue();
            if (change[0] != change[1]) {
                getLogger().info(
                    "Epsilon kalibrasyonu " + entry.getKey()
                        + ": " + change[0] + " -> " + change[1]);
            }
        }
        for (String groupId : calibrationInput.keySet()) {
            if (!changes.containsKey(groupId)) {
                getLogger().warning(
                    "Kalibrasyon raporunda girdi grubu yok: " + groupId);
            }
        }

        String snapshotDayKey = closedDayKey == null
            ? clock.dayKey() : closedDayKey;
        final List<DailySnapshot> dailySnapshots =
            new ArrayList<DailySnapshot>();
        for (String itemId : parsedItems.items.keySet()) {
            dailySnapshots.add(new DailySnapshot(
                itemId, engine.midPrice(itemId)));
        }
        queueDailySnapshots(snapshotDayKey, dailySnapshots);
        queueProfileSave();
    }

    private void queueDailySnapshots(
            final String dayKey, final List<DailySnapshot> snapshots) {
        if (asyncWriter == null) {
            return;
        }
        asyncWriter.submit(new Runnable() {
            @Override
            public void run() {
                for (DailySnapshot snapshot : snapshots) {
                    snapshotRepo.saveDaily(
                        dayKey, snapshot.itemId, snapshot.mid);
                }
            }
        });
    }

    private void queueStateAutosave() {
        if (asyncWriter == null) {
            return;
        }
        try {
            final List<StateSnapshot> snapshots = captureStateSnapshots();
            asyncWriter.submit(new Runnable() {
                @Override
                public void run() {
                    saveStateSnapshots(snapshots);
                }
            });
        } catch (RuntimeException failure) {
            getLogger().log(Level.SEVERE, "Durum otomatik kaydi siralanamadi", failure);
        }
    }

    private void queueProfileSave() {
        if (asyncWriter == null) {
            return;
        }
        final List<ProfileSnapshot> snapshots = captureProfileSnapshots();
        asyncWriter.submit(new Runnable() {
            @Override
            public void run() {
                saveProfileSnapshots(snapshots);
            }
        });
    }

    private List<StateSnapshot> captureStateSnapshots() {
        if (engine == null || parsedItems == null) {
            return Collections.emptyList();
        }
        List<StateSnapshot> snapshots = new ArrayList<StateSnapshot>();
        for (String groupId : parsedItems.groups.keySet()) {
            GroupState state = engine.state(groupId);
            if (state != null) {
                snapshots.add(new StateSnapshot(
                    groupId, state.stock, state.epsilon));
            }
        }
        return snapshots;
    }

    private List<ProfileSnapshot> captureProfileSnapshots() {
        if (signalTracker == null || parsedItems == null) {
            return Collections.emptyList();
        }
        List<ProfileSnapshot> snapshots = new ArrayList<ProfileSnapshot>();
        for (String groupId : parsedItems.groups.keySet()) {
            VolumeProfile profile = signalTracker.profile(groupId);
            snapshots.add(new ProfileSnapshot(
                groupId, profile.emaSnapshot(), profile.countSnapshot()));
        }
        return snapshots;
    }

    private void saveStatesSynchronously() {
        if (stateRepo == null) {
            return;
        }
        try {
            saveStateSnapshots(captureStateSnapshots());
        } catch (RuntimeException failure) {
            getLogger().log(Level.SEVERE, "Son grup durumu kaydi basarisiz oldu", failure);
        }
    }

    private void saveProfilesSynchronously() {
        if (profileRepo == null) {
            return;
        }
        try {
            saveProfileSnapshots(captureProfileSnapshots());
        } catch (RuntimeException failure) {
            getLogger().log(Level.SEVERE, "Son profil kaydi basarisiz oldu", failure);
        }
    }

    private void saveStateSnapshots(List<StateSnapshot> snapshots) {
        for (StateSnapshot snapshot : snapshots) {
            stateRepo.save(snapshot.groupId, snapshot.stock, snapshot.epsilon);
        }
    }

    private void saveProfileSnapshots(List<ProfileSnapshot> snapshots) {
        for (ProfileSnapshot snapshot : snapshots) {
            profileRepo.save(snapshot.groupId, snapshot.ema, snapshot.count);
        }
    }

    private void cancelAllSchedulerTasks() {
        try {
            cancelSchedulerTasksStrict();
        } catch (RuntimeException failure) {
            getLogger().log(Level.WARNING, "Zamanlayicilar iptal edilemedi", failure);
        }
    }

    private void cancelSchedulerTasksStrict() {
        try {
            getServer().getScheduler().cancelTasks(this);
        } finally {
            tasksScheduled = false;
        }
    }

    private void logLoadedDefinitions() {
        getLogger().info(
            "Yuklenenler: item=" + parsedItems.items.size()
                + ", grup=" + parsedItems.groups.size()
                + ", kategori=" + parsedItems.categories.size());
        if (!parsedItems.skipped.isEmpty()) {
            getLogger().warning(
                "Atlanan item/grup kimlikleri: " + parsedItems.skipped);
        }
    }

    private void activateSafeMode(String message, Throwable failure) {
        activateSafeMode(
            safeMode, "internal-error", message, message, failure);
    }

    private void activateSafeMode(
            SafeMode targetSafeMode, String message, Throwable failure) {
        activateSafeMode(
            targetSafeMode, "internal-error", message, message, failure);
    }

    private void activateSafeMode(String reasonCode,
                                  String safeDetail,
                                  String logMessage,
                                  Throwable failure) {
        activateSafeMode(
            safeMode, reasonCode, safeDetail, logMessage, failure);
    }

    private void activateSafeMode(SafeMode targetSafeMode,
                                  String reasonCode,
                                  String safeDetail,
                                  String logMessage,
                                  Throwable failure) {
        boolean activated = targetSafeMode != null
            && targetSafeMode.activate(reasonCode, safeDetail);
        String completeMessage = logMessage
            + " code=" + reasonCode + " detail=" + safeDetail;
        if (failure == null) {
            getLogger().severe(completeMessage);
        } else {
            getLogger().log(Level.SEVERE, completeMessage, failure);
        }
        if (activated && targetSafeMode == safeMode) {
            notifySafeModeAdmins(targetSafeMode);
        }
    }

    private PluginSettings loadDefaultSettings(SafeMode targetSafeMode) {
        try {
            return ConfigLoader.load(loadBundledYaml("config.yml"));
        } catch (IOException | InvalidConfigurationException | RuntimeException failure) {
            activateSafeMode(
                targetSafeMode, "Paket config.yml yuklenemedi", failure);
            return new PluginSettings(
                new EngineParams(
                    0.10, 0.05, 0.5, 0.5, 3.0, 1.0,
                    0.25, 1.5, 0.6, 0.25, 4.0, 2000.0, 2304),
                false, false, false, false, false,
                10, "tr", 20000.0, 0.3, 3);
        }
    }

    private Messages loadDefaultMessages(
            String language, SafeMode targetSafeMode) {
        try {
            return loadBundledMessages("messages_" + language + ".yml");
        } catch (IOException | RuntimeException languageFailure) {
            try {
                return loadBundledMessages("messages_tr.yml");
            } catch (IOException | RuntimeException fallbackFailure) {
                languageFailure.addSuppressed(fallbackFailure);
                activateSafeMode(
                    targetSafeMode,
                    "Paket mesajlari yuklenemedi",
                    languageFailure);
                return Messages.load(new StringReader(""));
            }
        }
    }

    private ItemNames loadDefaultItemNames(
            String language, SafeMode targetSafeMode) {
        try {
            return loadBundledItemNames("item-names_" + language + ".yml");
        } catch (IOException | RuntimeException languageFailure) {
            try {
                return loadBundledItemNames("item-names_en.yml");
            } catch (IOException | RuntimeException fallbackFailure) {
                languageFailure.addSuppressed(fallbackFailure);
                activateSafeMode(
                    targetSafeMode,
                    "Paket urun adlari yuklenemedi",
                    languageFailure);
                return ItemNames.empty();
            }
        }
    }

    private ParsedItems loadDefaultItems(
            PluginSettings targetSettings, SafeMode targetSafeMode) {
        try {
            return ItemsLoader.load(
                loadBundledYaml(ITEMS_FILE_NAME),
                targetSettings.varsayilanTabanStok,
                targetSettings.hassasiyet,
                targetSettings.maxTabanFiyat);
        } catch (IOException | InvalidConfigurationException | RuntimeException failure) {
            activateSafeMode(
                targetSafeMode, "Paket items.yml yuklenemedi", failure);
            return new ParsedItems(
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyList(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyList());
        }
    }

    private static boolean isSafeLanguage(String language) {
        return language != null && language.matches("[A-Za-z0-9_-]+");
    }

    private static YamlConfiguration loadYaml(File file)
            throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        return yaml;
    }

    private YamlConfiguration loadBundledYaml(String resourceName)
            throws IOException, InvalidConfigurationException {
        InputStream stream = getResource(resourceName);
        if (stream == null) {
            throw new IOException("Paket kaynagi bulunamadi: " + resourceName);
        }
        try (Reader reader = new InputStreamReader(
                stream, StandardCharsets.UTF_8)) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(reader);
            return yaml;
        }
    }

    private static Messages loadMessages(File file)
            throws IOException, InvalidConfigurationException {
        loadYaml(file);
        try (Reader reader = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            Messages loaded = Messages.load(reader);
            if (loaded.keys().isEmpty()) {
                throw new IOException("Mesaj dosyasi bos: " + file);
            }
            return loaded;
        }
    }

    private static ItemNames loadItemNames(File file)
            throws IOException, InvalidConfigurationException {
        loadYaml(file);
        try (Reader reader = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            ItemNames loaded = ItemNames.load(reader);
            if (loaded.keys().isEmpty()) {
                throw new IOException("Urun adi dosyasi bos: " + file);
            }
            return loaded;
        }
    }

    private Messages loadBundledMessages(String resourceName) throws IOException {
        InputStream stream = getResource(resourceName);
        if (stream == null) {
            throw new IOException("Paket kaynagi bulunamadi: " + resourceName);
        }
        try (Reader reader = new InputStreamReader(
                stream, StandardCharsets.UTF_8)) {
            Messages loaded = Messages.load(reader);
            if (loaded.keys().isEmpty()) {
                throw new IOException("Paket mesaj dosyasi bos: " + resourceName);
            }
            return loaded;
        }
    }

    private ItemNames loadBundledItemNames(String resourceName)
            throws IOException {
        InputStream stream = getResource(resourceName);
        if (stream == null) {
            throw new IOException("Paket kaynagi bulunamadi: " + resourceName);
        }
        try (Reader reader = new InputStreamReader(
                stream, StandardCharsets.UTF_8)) {
            ItemNames loaded = ItemNames.load(reader);
            if (loaded.keys().isEmpty()) {
                throw new IOException(
                    "Paket urun adi dosyasi bos: " + resourceName);
            }
            return loaded;
        }
    }

    private static final class Definitions {
        private final PluginSettings settings;
        private final Messages messages;
        private final ItemNames itemNames;
        private final ParsedItems parsedItems;
        private final boolean successful;

        private Definitions(PluginSettings settings,
                            Messages messages,
                            ItemNames itemNames,
                            ParsedItems parsedItems,
                            boolean successful) {
            this.settings = settings;
            this.messages = messages;
            this.itemNames = itemNames;
            this.parsedItems = parsedItems;
            this.successful = successful;
        }
    }

    private static final class RuntimeServices {
        private final Definitions definitions;
        private final SafeMode safeMode;
        private final PriceEngine engine;
        private final SignalTracker signalTracker;
        private final MarketService marketService;
        private final Change24h change24h;
        private final ApiImpl api;
        private final MarketCommand marketCommand;
        private final AdminCommands adminCommands;
        private final Listener guiListener;
        private final Listener signShopListener;
        private PapiHook papiHook;

        private RuntimeServices(Definitions definitions,
                                SafeMode safeMode,
                                PriceEngine engine,
                                SignalTracker signalTracker,
                                MarketService marketService,
                                Change24h change24h,
                                ApiImpl api,
                                MarketCommand marketCommand,
                                AdminCommands adminCommands,
                                Listener guiListener,
                                Listener signShopListener,
                                PapiHook papiHook) {
            this.definitions = definitions;
            this.safeMode = safeMode;
            this.engine = engine;
            this.signalTracker = signalTracker;
            this.marketService = marketService;
            this.change24h = change24h;
            this.api = api;
            this.marketCommand = marketCommand;
            this.adminCommands = adminCommands;
            this.guiListener = guiListener;
            this.signShopListener = signShopListener;
            this.papiHook = papiHook;
        }
    }

    private static final class DailySnapshot {
        private final String itemId;
        private final double mid;

        private DailySnapshot(String itemId, double mid) {
            this.itemId = itemId;
            this.mid = mid;
        }
    }

    private static final class StateSnapshot {
        private final String groupId;
        private final double stock;
        private final double epsilon;

        private StateSnapshot(String groupId, double stock, double epsilon) {
            this.groupId = groupId;
            this.stock = stock;
            this.epsilon = epsilon;
        }
    }

    private static final class ProfileSnapshot {
        private final String groupId;
        private final double[] ema;
        private final int[] count;

        private ProfileSnapshot(String groupId, double[] ema, int[] count) {
            this.groupId = groupId;
            this.ema = ema;
            this.count = count;
        }
    }

    private static final class UnavailableEconomy implements EconomyBridge {
        private static final UnavailableEconomy INSTANCE = new UnavailableEconomy();

        @Override
        public boolean has(java.util.UUID player, double amount) {
            return false;
        }

        @Override
        public boolean withdraw(java.util.UUID player, double amount) {
            return false;
        }

        @Override
        public boolean deposit(java.util.UUID player, double amount) {
            return false;
        }
    }
}
