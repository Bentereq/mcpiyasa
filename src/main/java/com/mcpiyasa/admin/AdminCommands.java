package com.mcpiyasa.admin;

import com.mcpiyasa.access.MarketCommand;
import com.mcpiyasa.compat.Materials;
import com.mcpiyasa.config.ItemsLoader;
import com.mcpiyasa.config.Messages;
import com.mcpiyasa.config.ParsedItems;
import com.mcpiyasa.diag.SafeMode;
import com.mcpiyasa.engine.GroupDef;
import com.mcpiyasa.engine.GroupState;
import com.mcpiyasa.engine.ItemDef;
import com.mcpiyasa.engine.PriceEngine;
import com.mcpiyasa.engine.TradeContext;
import com.mcpiyasa.engine.TradeSide;
import com.mcpiyasa.gui.AdminGuiActions;
import com.mcpiyasa.gui.Icons;
import com.mcpiyasa.gui.PriceStep;
import com.mcpiyasa.storage.StateRepo;
import com.mcpiyasa.storage.StorageFailureHandler;
import com.mcpiyasa.storage.AsyncWriter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** /market admin alt komutlarini ilgili servis ve dosyalara yonlendirir. */
public final class AdminCommands
        implements MarketCommand.AdminDelegate, AdminGuiActions {
    private static final int STATUS_SKIPPED_LIMIT = 5;
    private static final long RESET_WRITER_BARRIER_MS = 5000L;
    /** GUI'den eklenen yeni item'in baslangic taban fiyati; admin menuden ayarlar. */
    private static final double DEFAULT_GUI_PRICE = 1.0;
    private static final String DATABASE_FILE_NAME = "mcpiyasa.db";
    /** Sohbet listelemesinin sayfa basina urun tavani. */
    static final int LIST_PAGE_SIZE = 20;
    private static final List<String> SUBCOMMANDS = Collections.unmodifiableList(
        Arrays.asList(
            "reload", "durum", "liste", "fiyat", "sifirla",
            "itemekle", "itemcikar", "aktif"));
    private static final List<String> AKTIF_ARGS = Collections.unmodifiableList(
        Arrays.asList("ac", "kapa"));

    private final ParsedItems parsedItems;
    private final Messages messages;
    private final PriceEngine engine;
    /** Taban fiyatin (grup/bagimsiz) asamayacagi ust sinir. */
    private final double maxTabanFiyat;
    private final SafeMode safeMode;
    private final File itemsFile;
    private final File databaseFile;
    private final Reloader reloader;
    private final StateRepo stateRepo;
    private final StorageFailureHandler storageFailureHandler;
    private final Logger logger;
    private final AsyncWriter asyncWriter;

    public AdminCommands(ParsedItems parsedItems,
                         Messages messages,
                         PriceEngine engine,
                         double maxTabanFiyat,
                         SafeMode safeMode,
                         File itemsFile,
                         Reloader reloader,
                         StateRepo stateRepo,
                         StorageFailureHandler storageFailureHandler,
                         AsyncWriter asyncWriter,
                         Logger logger) {
        if (parsedItems == null || messages == null || engine == null
                || safeMode == null || itemsFile == null || reloader == null
                || stateRepo == null || storageFailureHandler == null
                || asyncWriter == null || logger == null) {
            throw new IllegalArgumentException(
                "AdminCommands bagimliliklari null olamaz");
        }
        if (!Double.isFinite(maxTabanFiyat) || maxTabanFiyat <= 0.0) {
            throw new IllegalArgumentException(
                "maxTabanFiyat sonlu ve 0'dan buyuk olmali");
        }
        this.parsedItems = parsedItems;
        this.messages = messages;
        this.engine = engine;
        this.maxTabanFiyat = maxTabanFiyat;
        this.safeMode = safeMode;
        this.itemsFile = itemsFile;
        this.databaseFile = new File(
            itemsFile.getAbsoluteFile().getParentFile(), DATABASE_FILE_NAME);
        this.reloader = reloader;
        this.stateRepo = stateRepo;
        this.storageFailureHandler = storageFailureHandler;
        this.asyncWriter = asyncWriter;
        this.logger = logger;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (sender == null || args == null || args.length == 0) {
            return false;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if ("reload".equals(subcommand)) {
            if (args.length != 1) {
                return false;
            }
            reloadAndReply(sender);
            return true;
        }
        if ("durum".equals(subcommand)) {
            if (args.length != 1) {
                return false;
            }
            showStatus(sender);
            return true;
        }
        if ("liste".equals(subcommand)) {
            return listItems(sender, args);
        }
        if ("fiyat".equals(subcommand)) {
            return setPrice(sender, args);
        }
        if ("sifirla".equals(subcommand)) {
            return resetPrice(sender, args);
        }
        if ("itemekle".equals(subcommand)) {
            return addItem(sender, args);
        }
        if ("itemcikar".equals(subcommand)) {
            return removeItem(sender, args);
        }
        if ("aktif".equals(subcommand)) {
            return setActive(sender, args);
        }
        return false;
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args == null || args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return SUBCOMMANDS;
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if ("liste".equals(subcommand)) {
            return args.length == 2
                ? categoryIds() : Collections.<String>emptyList();
        }
        if ("sifirla".equals(subcommand) || "itemcikar".equals(subcommand)
                || "fiyat".equals(subcommand)) {
            return args.length == 2
                ? loadedItemIds() : Collections.<String>emptyList();
        }
        if ("aktif".equals(subcommand)) {
            if (args.length == 2) {
                return loadedItemIds();
            }
            return args.length == 3
                ? AKTIF_ARGS : Collections.<String>emptyList();
        }
        if ("itemekle".equals(subcommand)) {
            return completeAddItem(sender, args);
        }
        return Collections.emptyList();
    }

    /**
     * itemekle iki bicimi destekler; tamamlama tokenin hangi bicimde hangi
     * alana denk geldigine gore secilir.
     */
    private List<String> completeAddItem(CommandSender sender, String[] args) {
        boolean heldForm = parsePositiveNumber(args[1]) != null;
        if (args.length == 2) {
            List<String> candidates = new ArrayList<String>();
            String held = heldMaterialName(sender);
            if (held != null) {
                candidates.add(held);
            }
            for (String itemId : loadedItemIds()) {
                if (!itemId.equals(held)) {
                    candidates.add(itemId);
                }
            }
            return candidates;
        }
        int categoryFrom = heldForm ? 3 : 4;
        return args.length >= categoryFrom
            ? categoryIds() : Collections.<String>emptyList();
    }

    private void showStatus(CommandSender sender) {
        StringBuilder status = new StringBuilder();
        appendStatus(status, messages.get("admin.durum-guvenli-mod"),
            Boolean.toString(safeMode.isActive()));
        appendStatus(status, messages.get("admin.durum-urunler"),
            Integer.toString(parsedItems.items.size()));
        appendStatus(status, messages.get("admin.durum-gruplar"),
            Integer.toString(parsedItems.groups.size()));
        appendStatus(status, messages.get("admin.durum-atlananlar"),
            Integer.toString(parsedItems.skipped.size()));
        appendSkippedIds(status, parsedItems.skipped);
        appendStatus(status, messages.get("admin.durum-veritabani"),
            databaseFile.getAbsolutePath());
        if (!safeMode.reason().isEmpty()) {
            appendStatus(status, messages.get("admin.durum-neden"),
                safeMode.reason());
            if (!safeMode.detail().isEmpty()) {
                status.append(' ').append(messages.get("admin.durum-detay"))
                    .append('=').append(safeMode.detail());
            }
        }

        sender.sendMessage(messages.get(
            "admin.durum", singletonVar("durum", status.toString())));
    }

    /** {@code liste [kategori] [sayfa]} - salt okunur, islem yaratmaz. */
    private boolean listItems(CommandSender sender, String[] args) {
        if (args.length > 3) {
            return false;
        }
        String category = null;
        int page = 1;
        if (args.length >= 2) {
            Integer parsedPage = parsePage(args[1]);
            if (parsedPage != null) {
                page = parsedPage.intValue();
            } else {
                category = args[1];
                if (!parsedItems.categoryItems.containsKey(category)) {
                    sender.sendMessage(messages.get(
                        "admin.kategori-yok",
                        singletonVar("kategori", category)));
                    return true;
                }
            }
        }
        if (args.length == 3) {
            Integer parsedPage = parsePage(args[2]);
            if (category == null || parsedPage == null) {
                return false;
            }
            page = parsedPage.intValue();
        }

        List<String[]> entries = listEntries(category);
        if (entries.isEmpty()) {
            sender.sendMessage(messages.get("admin.liste-bos"));
            return true;
        }
        int pageCount = (entries.size() - 1) / LIST_PAGE_SIZE + 1;
        if (page > pageCount) {
            page = pageCount;
        }
        int from = (page - 1) * LIST_PAGE_SIZE;
        int to = Math.min(entries.size(), from + LIST_PAGE_SIZE);

        Map<String, String> headerVars = new LinkedHashMap<String, String>();
        headerVars.put("sayfa", Integer.toString(page));
        headerVars.put("toplam-sayfa", Integer.toString(pageCount));
        headerVars.put("adet", Integer.toString(entries.size()));
        sender.sendMessage(messages.get("admin.liste-baslik", headerVars));

        String shownCategory = null;
        for (int index = from; index < to; index++) {
            String[] entry = entries.get(index);
            if (index == from || !equalCategory(shownCategory, entry[0])) {
                shownCategory = entry[0];
                sender.sendMessage(entry[0] == null
                    ? messages.get("admin.liste-kategorisiz")
                    : messages.get("admin.liste-kategori",
                        singletonVar("kategori", entry[0])));
            }
            Map<String, String> rowVars = new LinkedHashMap<String, String>();
            rowVars.put("item", entry[1]);
            rowVars.put("alis", unitPrice(entry[1], TradeSide.BUY));
            rowVars.put("satis", unitPrice(entry[1], TradeSide.SELL));
            sender.sendMessage(messages.get("admin.liste-satir", rowVars));
        }
        return true;
    }

    /** Kategori sirasina gore {kategori, itemId} ciftleri; sonda kategorisizler. */
    private List<String[]> listEntries(String category) {
        List<String[]> entries = new ArrayList<String[]>();
        if (category != null) {
            for (String itemId : categoryItemIds(category)) {
                entries.add(new String[] {category, itemId});
            }
            return entries;
        }
        for (com.mcpiyasa.config.CategoryDef categoryDef
                : parsedItems.categories) {
            for (String itemId : categoryItemIds(categoryDef.id)) {
                entries.add(new String[] {categoryDef.id, itemId});
            }
        }
        for (String itemId : parsedItems.items.keySet()) {
            if (!parsedItems.itemCategory.containsKey(itemId)) {
                entries.add(new String[] {null, itemId});
            }
        }
        return entries;
    }

    private List<String> categoryItemIds(String category) {
        List<String> itemIds = parsedItems.categoryItems.get(category);
        return itemIds == null ? Collections.<String>emptyList() : itemIds;
    }

    /**
     * GUI ile ayni salt-okunur teklif yolu; baglam NEUTRAL ve commit yok.
     */
    private String unitPrice(String itemId, TradeSide side) {
        ItemDef item = parsedItems.items.get(itemId);
        if (item == null || !item.isTradeEnabled(side)) {
            return messages.get("admin.liste-kapali");
        }
        try {
            return Icons.money(
                engine.quote(itemId, 1, side, TradeContext.NEUTRAL).unitAvg);
        } catch (RuntimeException | LinkageError ignored) {
            return "-";
        }
    }

    private boolean resetPrice(CommandSender sender, String[] args) {
        if (args.length != 2) {
            return false;
        }
        resetPriceById(sender, args[1]);
        return true;
    }

    /**
     * Item grubunu taban stoka sifirlar; metin komutu ve GUI ayni yolu kullanir.
     *
     * @return sifirlama uygulandiysa true
     */
    private boolean resetPriceById(CommandSender sender, String rawItem) {
        Material material = Materials.resolve(rawItem);
        String itemId = material == null ? null : material.name();
        ItemDef item = itemId == null ? null : parsedItems.items.get(itemId);
        if (item == null) {
            sendUnknownItem(sender, rawItem);
            return false;
        }

        GroupDef group = parsedItems.groups.get(item.groupId);
        GroupState state = engine.state(item.groupId);
        if (group == null || state == null) {
            sender.sendMessage(messages.get("islem.hata"));
            return false;
        }
        if (!asyncWriter.awaitIdle(RESET_WRITER_BARRIER_MS)) {
            IllegalStateException failure = new IllegalStateException(
                "Sifirlama oncesi async writer zamaninda bosalmadi");
            logger.log(Level.SEVERE, failure.getMessage(), failure);
            reportStorageFailure(failure);
            sender.sendMessage(messages.get("islem.hata"));
            return false;
        }
        double previousStock = state.stock;
        double previousEpsilon = state.epsilon;
        engine.restoreState(group.id, group.baseStock, previousEpsilon);
        try {
            stateRepo.save(group.id, group.baseStock, previousEpsilon);
        } catch (RuntimeException | LinkageError failure) {
            engine.restoreState(group.id, previousStock, previousEpsilon);
            logger.log(
                Level.SEVERE,
                "Admin fiyat sifirlama durumu kaydedilemedi"
                    + " groupId=" + group.id,
                failure);
            reportStorageFailure(failure);
            sender.sendMessage(messages.get("islem.hata"));
            return false;
        }
        sender.sendMessage(messages.get(
            "admin.fiyat-sifirlandi", singletonVar("item", itemId)));
        return true;
    }

    /**
     * {@code fiyat <item> <yeni-taban-fiyat>} - itemekle'nin gruplu/bagimsiz
     * yazma kurallarini aynen kullanir, yalniz sonucu birim fiyat olarak
     * raporlar.
     */
    private boolean setPrice(CommandSender sender, String[] args) {
        if (args.length != 3) {
            return false;
        }
        Material material = Materials.resolve(args[1]);
        String itemId = material == null ? null : material.name();
        ItemDef item = itemId == null ? null : parsedItems.items.get(itemId);
        GroupDef group = item == null
            ? null : parsedItems.groups.get(item.groupId);
        if (item == null || group == null) {
            sendUnknownItem(sender, args[1]);
            return true;
        }
        Double newPrice = parsePositiveNumber(args[2]);
        if (newPrice == null) {
            return false;
        }

        writePriceAndReload(
            sender, material, item, group, newPrice.doubleValue(), true);
        return true;
    }

    /**
     * fiyat komut argumanini (gruplu item'da birim fiyat, bagimsizda
     * taban-fiyat) tek yazma noktasi {@link #writeItemDefinition} ile yazar,
     * eski-&gt;yeni birim fiyati bildirir ve tanimi yeniden yukler. Boylece
     * gruplu/bagimsiz taban-agirlik matematigi tek yerde kalir; GUI de bu yolu
     * cagirir.
     *
     * @param announceReload false ise reload sohbet satiri bastirilir (GUI)
     * @return {eski, yeni} birim fiyat; hata halinde {@code null}
     */
    private double[] writePriceAndReload(CommandSender sender, Material material,
                                         ItemDef item, GroupDef group,
                                         double fiyatArg,
                                         boolean announceReload) {
        double oldUnitPrice = group.basePrice * item.weight;
        WriteResult written = writeItemDefinition(
            sender, "fiyat", material, fiyatArg, null, null);
        if (written == null) {
            return null;
        }
        // written.writtenBase, tavana kirpilmis olabilecek GERCEKTEN yazilan
        // grup/bagimsiz taban fiyatidir; birim fiyat her zaman taban*agirlik
        // (written.weight, YAML'dan okunan gercek agirlik).
        double newUnitPrice = written.writtenBase * written.weight;
        Map<String, String> vars = new LinkedHashMap<String, String>();
        vars.put("item", material.name());
        vars.put("eski", Icons.money(oldUnitPrice));
        vars.put("yeni", Icons.money(newUnitPrice));
        sender.sendMessage(messages.get("admin.fiyat-guncellendi", vars));
        if (written.clampedToMax) {
            sender.sendMessage(messages.get(
                "admin.azami-fiyat",
                singletonVar("deger", Icons.money(written.writtenBase))));
        }
        if (announceReload) {
            reloadAndReply(sender);
        } else {
            reloadQuietly();
        }
        return new double[] {oldUnitPrice, newUnitPrice};
    }

    /**
     * {@code itemekle [<MATERIAL>] <taban-fiyat> [taban-stok] [kategori]}.
     * Ilk token sayiysa materyal ana eldeki item'dan alinir.
     */
    private boolean addItem(CommandSender sender, String[] args) {
        int index = 1;
        Material material;
        if (args.length > 1 && parsePositiveNumber(args[1]) != null) {
            material = requireHeldMaterial(sender);
            if (material == null) {
                return true;
            }
        } else {
            if (args.length < 3) {
                return false;
            }
            material = Materials.resolve(args[1]);
            if (!Materials.isMarketable(material)) {
                sendUnknownItem(sender, args[1]);
                return true;
            }
            index = 2;
        }

        if (index >= args.length) {
            return false;
        }
        Double basePrice = parsePositiveNumber(args[index]);
        if (basePrice == null) {
            return false;
        }
        index++;

        Double baseStock = null;
        String category = null;
        int remaining = args.length - index;
        if (remaining > 2) {
            return false;
        }
        if (remaining == 2) {
            baseStock = parsePositiveNumber(args[index]);
            if (baseStock == null) {
                return false;
            }
            category = args[index + 1];
        } else if (remaining == 1) {
            baseStock = parsePositiveNumber(args[index]);
            if (baseStock == null) {
                category = args[index];
            }
        }

        WriteResult written = writeItemDefinition(
            sender, "itemekle", material, basePrice.doubleValue(), baseStock,
            category);
        if (written == null) {
            return true;
        }
        if (written.newCategory) {
            sender.sendMessage(messages.get(
                "admin.yeni-kategori", singletonVar("kategori", category)));
        }
        reloadAndReply(sender);
        return true;
    }

    /**
     * items.yml'e tek yazma noktasi. Gruplu item'da grup tabani
     * {@code istenen/agirlik} olarak guncellenir; bagimsiz item kendi
     * taban-fiyat'ini alir.
     *
     * @return yazma sonucu, hata halinde null (yanit gonderilmistir)
     */
    private WriteResult writeItemDefinition(CommandSender sender,
                                            String operation,
                                            Material material,
                                            double basePrice,
                                            Double baseStock,
                                            String category) {
        WriteResult result = new WriteResult();
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            if (itemsFile.exists()) {
                yaml.load(itemsFile);
            }
            String itemPath = "itemler." + material.name();
            String groupId = yaml.getString(itemPath + ".grup");
            // agirlik hem gruplu hem bagimsiz item icin birim fiyati
            // taban*agirlik yapar (ItemsLoader); tek yazma noktasi ikisinde
            // de ayni YAML kaynagini okur, boylece bildirilen birim fiyat
            // her zaman GERCEKTEN yazilan degerle tutarlidir.
            Object rawWeight = yaml.get(itemPath + ".agirlik");
            double weight = 1.0;
            if (rawWeight != null) {
                if (!(rawWeight instanceof Number)) {
                    sender.sendMessage(messages.get("islem.hata"));
                    return null;
                }
                weight = ((Number) rawWeight).doubleValue();
                if (!Double.isFinite(weight) || weight <= 0.0) {
                    sender.sendMessage(messages.get("islem.hata"));
                    return null;
                }
            }
            result.weight = weight;
            if (groupId != null) {
                String groupPath = "gruplar." + groupId;
                if (!yaml.isConfigurationSection(groupPath)) {
                    sender.sendMessage(messages.get("islem.hata"));
                    return null;
                }
                // KUCUK: cok kucuk agirlik + buyuk fiyat -> .inf/NaN olabilir;
                // sessizce grubu kaybetmek yerine burada reddediyoruz.
                double writtenBase = basePrice / weight;
                if (!Double.isFinite(writtenBase) || writtenBase <= 0.0) {
                    sender.sendMessage(messages.get("islem.hata"));
                    return null;
                }
                result.grouped = true;
                result.clampedToMax = writtenBase > maxTabanFiyat;
                result.writtenBase = clampBasePrice(writtenBase);
                yaml.set(groupPath + ".taban-fiyat", result.writtenBase);
                if (baseStock != null) {
                    yaml.set(
                        groupPath + ".taban-stok",
                        baseStock.doubleValue() * weight);
                }
            } else {
                if (!Double.isFinite(basePrice) || basePrice <= 0.0) {
                    sender.sendMessage(messages.get("islem.hata"));
                    return null;
                }
                result.clampedToMax = basePrice > maxTabanFiyat;
                result.writtenBase = clampBasePrice(basePrice);
                yaml.set(itemPath + ".taban-fiyat", result.writtenBase);
                if (baseStock != null) {
                    yaml.set(itemPath + ".taban-stok", baseStock);
                }
            }
            if (category != null) {
                if (!ensureCategory(sender, yaml, category, material, result)) {
                    return null;
                }
                yaml.set(itemPath + ".kategori", category);
            }
            yaml.save(itemsFile);
        } catch (IOException | InvalidConfigurationException | RuntimeException failure) {
            reportItemsFileFailure(sender, operation, failure);
            return null;
        }
        return result;
    }

    /**
     * Bilinmeyen kategori adi verildiginde bolum de olusturulur; aksi halde
     * loader item'i "kategori bilinmiyor" diye atlar ve reload reddedilir.
     */
    private boolean ensureCategory(CommandSender sender,
                                   YamlConfiguration yaml,
                                   String category,
                                   Material material,
                                   WriteResult result) {
        String categoryPath = "kategoriler." + category;
        if (yaml.isConfigurationSection(categoryPath)) {
            return true;
        }
        ConfigurationSection section =
            yaml.getConfigurationSection("kategoriler");
        int existing = section == null ? 0 : section.getKeys(false).size();
        if (existing >= ItemsLoader.MAX_CATEGORIES) {
            sender.sendMessage(messages.get(
                "admin.kategori-dolu",
                singletonVar(
                    "limit", Integer.toString(ItemsLoader.MAX_CATEGORIES))));
            return false;
        }
        yaml.set(categoryPath + ".ikon", material.name());
        yaml.set(categoryPath + ".sira", Integer.valueOf(nextOrder(section)));
        result.newCategory = true;
        return true;
    }

    private static int nextOrder(ConfigurationSection section) {
        int highest = 0;
        if (section != null) {
            for (String id : section.getKeys(false)) {
                Object raw = section.get(id + ".sira");
                if (raw instanceof Number) {
                    highest = Math.max(highest, ((Number) raw).intValue());
                }
            }
        }
        return highest + 1;
    }

    private Material requireHeldMaterial(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.get("komut.sadece-oyuncu"));
            return null;
        }
        Material material = heldMaterial((Player) sender);
        if (material == null) {
            sender.sendMessage(messages.get("admin.elde-item-yok"));
        }
        return material;
    }

    private String heldMaterialName(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return null;
        }
        Material material = heldMaterial((Player) sender);
        return material == null ? null : material.name();
    }

    private static Material heldMaterial(Player player) {
        ItemStack held;
        try {
            held = player.getInventory() == null
                ? null : player.getInventory().getItemInMainHand();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
        Material material = held == null ? null : held.getType();
        return Materials.isMarketable(material) ? material : null;
    }

    private boolean removeItem(CommandSender sender, String[] args) {
        if (args.length != 2) {
            return false;
        }
        removeItemById(sender, args[1], true);
        return true;
    }

    /**
     * Item'i items.yml'den cikarir; metin komutu ve GUI ayni yolu kullanir.
     *
     * @param announceReload true ise reload-ok sohbeti; false ise GUI'ye ozel
     *     "cikarildi" satiri ve sessiz reload
     * @return item silindiyse true
     */
    private boolean removeItemById(CommandSender sender, String rawItem,
                                   boolean announceReload) {
        Material material = Materials.resolve(rawItem);
        if (material == null) {
            sendUnknownItem(sender, rawItem);
            return false;
        }

        try {
            YamlConfiguration yaml = new YamlConfiguration();
            if (itemsFile.exists()) {
                yaml.load(itemsFile);
            }
            String itemPath = "itemler." + material.name();
            if (!yaml.contains(itemPath)) {
                sendUnknownItem(sender, rawItem);
                return false;
            }
            yaml.set(itemPath, null);
            yaml.save(itemsFile);
        } catch (IOException | InvalidConfigurationException | RuntimeException failure) {
            reportItemsFileFailure(sender, "itemcikar", failure);
            return false;
        }
        if (announceReload) {
            reloadAndReply(sender);
        } else {
            sender.sendMessage(messages.get(
                "admin-gui.item-cikarildi",
                singletonVar("item", material.name())));
            reloadQuietly();
        }
        return true;
    }

    /**
     * {@code aktif <item> <ac|kapa>} - urunu markette acar/kapatir. Yon
     * bayraklariyla ayni gate'lerden gecer (metin admin komutu, mcpiyasa.admin)
     * ve tek YAML yazma noktasi {@link #writeActiveField} ile {@code aktif}
     * bayragini yazip yeniden yukler.
     */
    private boolean setActive(CommandSender sender, String[] args) {
        if (args.length != 3) {
            return false;
        }
        Boolean target = parseAcKapa(args[2]);
        if (target == null) {
            return false;
        }
        Material material = Materials.resolve(args[1]);
        String itemId = material == null ? null : material.name();
        ItemDef item = itemId == null ? null : parsedItems.items.get(itemId);
        if (material == null || item == null) {
            sendUnknownItem(sender, args[1]);
            return true;
        }
        if (writeActiveField(sender, material, target.booleanValue()) == null) {
            return true;
        }
        sender.sendMessage(messages.get(
            target.booleanValue()
                ? "admin.urun-aktiflestirildi"
                : "admin.urun-devre-disi-birakildi",
            singletonVar("item", itemId)));
        reloadAndReply(sender);
        return true;
    }

    /**
     * items.yml'e {@code itemler.<MATERIAL>.aktif} bayragini yazan tek nokta;
     * metin komutu ve GUI toggle'i ayni yolu kullanir.
     *
     * @return yazilan deger; hata halinde {@code null} (yanit gonderilmistir)
     */
    private Boolean writeActiveField(CommandSender sender, Material material,
                                     boolean target) {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            if (itemsFile.exists()) {
                yaml.load(itemsFile);
            }
            String itemPath = "itemler." + material.name();
            if (!yaml.contains(itemPath)) {
                sender.sendMessage(messages.get("islem.hata"));
                return null;
            }
            yaml.set(itemPath + ".aktif", Boolean.valueOf(target));
            yaml.save(itemsFile);
        } catch (IOException | InvalidConfigurationException | RuntimeException failure) {
            reportItemsFileFailure(sender, "aktif", failure);
            return null;
        }
        return Boolean.valueOf(target);
    }

    /** {@code ac/acik/true} -> true; {@code kapa/kapat/kapali/false} -> false. */
    private static Boolean parseAcKapa(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.toLowerCase(Locale.ROOT);
        if ("ac".equals(value) || "aç".equals(value) || "acik".equals(value)
                || "true".equals(value)) {
            return Boolean.TRUE;
        }
        if ("kapa".equals(value) || "kapat".equals(value)
                || "kapali".equals(value) || "false".equals(value)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private void reloadAndReply(CommandSender sender) {
        ReloadResult result;
        try {
            result = reloader.reload();
        } catch (RuntimeException | LinkageError failure) {
            logger.log(Level.SEVERE, "Admin reload cagrisi basarisiz", failure);
            result = ReloadResult.FAILED_CANDIDATE;
        }
        Messages activeMessages = reloader.activeMessages();
        sender.sendMessage((activeMessages == null ? messages : activeMessages)
            .get(result.messageKey()));
    }

    /** GUI icin sohbet satiri uretmeden yeniden yukler (geri bildirim menude). */
    private ReloadResult reloadQuietly() {
        try {
            return reloader.reload();
        } catch (RuntimeException | LinkageError failure) {
            logger.log(Level.SEVERE, "Admin GUI reload cagrisi basarisiz", failure);
            return ReloadResult.FAILED_CANDIDATE;
        }
    }

    private void sendUnknownItem(CommandSender sender, String rawItem) {
        sender.sendMessage(messages.get(
            "admin.item-yok", singletonVar("item", rawItem)));
    }

    private void reportStorageFailure(Throwable failure) {
        try {
            storageFailureHandler.handle(failure);
        } catch (RuntimeException | LinkageError alarmFailure) {
            failure.addSuppressed(alarmFailure);
            logger.log(
                Level.SEVERE,
                "Admin fiyat sifirlama storage alarmi calismadi",
                alarmFailure);
        }
    }

    private void reportItemsFileFailure(CommandSender sender,
                                        String operation,
                                        Throwable failure) {
        logger.log(
            Level.SEVERE,
            "Admin YAML islemi basarisiz file=" + itemsFile.getAbsolutePath()
                + " operation=" + operation,
            failure);
        Map<String, String> vars = new LinkedHashMap<String, String>();
        vars.put("islem", operation);
        vars.put("dosya", itemsFile.getAbsolutePath());
        sender.sendMessage(messages.get("admin.items-yaml-hatasi", vars));
    }

    private List<String> loadedItemIds() {
        return new ArrayList<String>(parsedItems.items.keySet());
    }

    private List<String> categoryIds() {
        return new ArrayList<String>(parsedItems.categoryItems.keySet());
    }

    static Double parsePositiveNumber(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            double value = Double.parseDouble(raw);
            if (!(value > 0.0) || Double.isInfinite(value)) {
                return null;
            }
            return Double.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static Integer parsePage(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            int page = Integer.parseInt(raw);
            return page < 1 ? null : Integer.valueOf(page);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean equalCategory(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static void appendSkippedIds(StringBuilder status, List<String> skipped) {
        int count = Math.min(STATUS_SKIPPED_LIMIT, skipped.size());
        for (int i = 0; i < count; i++) {
            status.append(i == 0 ? ':' : ',').append(skipped.get(i));
        }
    }

    private static void appendStatus(StringBuilder status,
                                     String field, String value) {
        if (status.length() > 0) {
            status.append('\n');
        }
        status.append(field).append('=').append(value);
    }

    private static Map<String, String> singletonVar(String key, String value) {
        Map<String, String> vars = new LinkedHashMap<String, String>();
        vars.put(key, value);
        return vars;
    }

    /**
     * Bir taban fiyati [PriceStep.MIN_PRICE, maxTabanFiyat] araligina
     * kirpar (long-cent birikiminde tasmayi engellemek icin).
     */
    private double clampBasePrice(double value) {
        if (value < PriceStep.MIN_PRICE) {
            return PriceStep.MIN_PRICE;
        }
        if (value > maxTabanFiyat) {
            return maxTabanFiyat;
        }
        return value;
    }

    /** items.yml yazmasinin hangi yolu izledigini raporlar. */
    private static final class WriteResult {
        private boolean grouped;
        private boolean newCategory;
        /** Gercekten yazilan (tavana kirpilmis) grup/bagimsiz taban fiyati. */
        private double writtenBase;
        /** true ise istenen deger maxTabanFiyat'i asip kirpildi. */
        private boolean clampedToMax;
        /** YAML'dan okunan agirlik (agirlik yoksa 1.0). */
        private double weight = 1.0;
    }

    // --- AdminGuiActions: tikla-yonet menusunun yazma/eylem yuzeyi ---

    @Override
    public double[] setBaseUnitPrice(CommandSender sender, String itemId,
                                     double newBaseUnit) {
        Material material = Materials.resolve(itemId);
        String resolvedId = material == null ? null : material.name();
        ItemDef item = resolvedId == null ? null : parsedItems.items.get(resolvedId);
        GroupDef group = item == null ? null : parsedItems.groups.get(item.groupId);
        if (material == null || item == null || group == null) {
            sendUnknownItem(sender, itemId);
            return null;
        }
        if (!Double.isFinite(newBaseUnit) || newBaseUnit <= 0.0) {
            sender.sendMessage(messages.get("islem.hata"));
            return null;
        }
        // fiyat komut argumani gruplu item'da birim, bagimsizda taban-fiyat;
        // hedef birim fiyati writeItemDefinition'in bekledigi arguman'a cevir.
        double fiyatArg = isGroupedInYaml(material)
            ? newBaseUnit : newBaseUnit / item.weight;
        return writePriceAndReload(sender, material, item, group, fiyatArg, false);
    }

    @Override
    public boolean toggleDirection(CommandSender sender, String itemId,
                                   TradeSide side) {
        Material material = Materials.resolve(itemId);
        String resolvedId = material == null ? null : material.name();
        ItemDef item = resolvedId == null ? null : parsedItems.items.get(resolvedId);
        if (material == null || item == null || side == null) {
            sendUnknownItem(sender, itemId);
            return false;
        }
        boolean target = !item.isTradeEnabled(side);
        String field = side == TradeSide.BUY ? "alis-acik" : "satis-acik";
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            if (itemsFile.exists()) {
                yaml.load(itemsFile);
            }
            String itemPath = "itemler." + material.name();
            if (!yaml.contains(itemPath)) {
                sender.sendMessage(messages.get("islem.hata"));
                return false;
            }
            yaml.set(itemPath + "." + field, Boolean.valueOf(target));
            yaml.save(itemsFile);
        } catch (IOException | InvalidConfigurationException | RuntimeException failure) {
            reportItemsFileFailure(sender, "yon", failure);
            return false;
        }
        reloadQuietly();
        return target;
    }

    @Override
    public boolean toggleActive(CommandSender sender, String itemId) {
        Material material = Materials.resolve(itemId);
        String resolvedId = material == null ? null : material.name();
        ItemDef item = resolvedId == null
            ? null : parsedItems.items.get(resolvedId);
        if (material == null || item == null) {
            sendUnknownItem(sender, itemId);
            return false;
        }
        boolean target = !item.active;
        if (writeActiveField(sender, material, target) == null) {
            return item.active;
        }
        reloadQuietly();
        return target;
    }

    @Override
    public boolean removeItem(CommandSender sender, String itemId) {
        return removeItemById(sender, itemId, false);
    }

    @Override
    public boolean resetItem(CommandSender sender, String itemId) {
        return resetPriceById(sender, itemId);
    }

    @Override
    public String addHeldItem(Player admin) {
        Material material = heldMaterial(admin);
        if (material == null) {
            admin.sendMessage(messages.get("admin.elde-item-yok"));
            return null;
        }
        String itemId = material.name();
        if (parsedItems.items.containsKey(itemId)) {
            return itemId;
        }
        WriteResult written = writeItemDefinition(
            admin, "itemekle", material, DEFAULT_GUI_PRICE, null, null);
        if (written == null) {
            return null;
        }
        admin.sendMessage(messages.get(
            "admin-gui.item-eklendi", singletonVar("item", itemId)));
        reloadQuietly();
        return itemId;
    }

    @Override
    public void reload(CommandSender admin) {
        reloadAndReply(admin);
    }

    @Override
    public void status(CommandSender admin) {
        showStatus(admin);
    }

    /**
     * items.yml'de bu materyalin bir gruba bagli olup olmadigi. fiyat argumani
     * gruplu item'da birim, bagimsizda taban-fiyat oldugu icin GUI bunu bilmeli;
     * taban-agirlik yazma matematigi yine writeItemDefinition'da kalir.
     */
    private boolean isGroupedInYaml(Material material) {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            if (itemsFile.exists()) {
                yaml.load(itemsFile);
            }
            return yaml.getString("itemler." + material.name() + ".grup") != null;
        } catch (IOException | InvalidConfigurationException | RuntimeException ignored) {
            return false;
        }
    }

    public interface Reloader {
        ReloadResult reload();
        Messages activeMessages();
    }
}
