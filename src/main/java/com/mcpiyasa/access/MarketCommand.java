package com.mcpiyasa.access;

import com.mcpiyasa.compat.Materials;
import com.mcpiyasa.config.ItemNames;
import com.mcpiyasa.config.Messages;
import com.mcpiyasa.config.ParsedItems;
import com.mcpiyasa.config.PluginSettings;
import com.mcpiyasa.engine.ItemDef;
import com.mcpiyasa.engine.PriceEngine;
import com.mcpiyasa.engine.Quote;
import com.mcpiyasa.engine.TradeSide;
import com.mcpiyasa.gui.AdminGuiHost;
import com.mcpiyasa.gui.AdminMenu;
import com.mcpiyasa.gui.Icons;
import com.mcpiyasa.gui.MainMenu;
import com.mcpiyasa.market.MarketResult;
import com.mcpiyasa.market.MarketService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** /market komut agacini market ve GUI servislerine yonlendirir. */
public final class MarketCommand implements CommandExecutor, TabCompleter {
    private static final String USE_PERMISSION = "mcpiyasa.use";
    private static final String ADMIN_PERMISSION = "mcpiyasa.admin";

    private final ParsedItems parsedItems;
    private final PluginSettings settings;
    private final Messages messages;
    private final ItemNames itemNames;
    private final PriceEngine engine;
    private final MarketService marketService;
    private AdminDelegate adminDelegate;
    private AdminGuiHost adminGuiHost;

    public MarketCommand(ParsedItems parsedItems,
                         PluginSettings settings,
                         Messages messages,
                         PriceEngine engine,
                         MarketService marketService) {
        this(parsedItems, settings, messages, ItemNames.empty(), engine,
            marketService);
    }

    public MarketCommand(ParsedItems parsedItems,
                         PluginSettings settings,
                         Messages messages,
                         ItemNames itemNames,
                         PriceEngine engine,
                         MarketService marketService) {
        if (parsedItems == null || settings == null || messages == null
                || itemNames == null || engine == null
                || marketService == null) {
            throw new IllegalArgumentException(
                "MarketCommand bagimliliklari null olamaz");
        }
        this.parsedItems = parsedItems;
        this.settings = settings;
        this.messages = messages;
        this.itemNames = itemNames;
        this.engine = engine;
        this.marketService = marketService;
    }

    /** Admin alt komutlarini canli runtime'in yoneticisine baglar. */
    public void setAdminDelegate(AdminDelegate adminDelegate) {
        this.adminDelegate = adminDelegate;
    }

    /** Tiklanabilir admin menusune canli veri/eylem saglayan kaynak. */
    public void setAdminGuiHost(AdminGuiHost adminGuiHost) {
        this.adminGuiHost = adminGuiHost;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        String[] safeArgs = args == null ? new String[0] : args;
        if (safeArgs.length == 0) {
            Player player = requirePlayerWithUsePermission(sender);
            if (player != null) {
                MainMenu.open(player, parsedItems, settings, messages);
            }
            return true;
        }

        String subcommand = safeArgs[0].toLowerCase(Locale.ROOT);
        if ("admin".equals(subcommand)) {
            handleAdmin(sender, command, safeArgs);
            return true;
        }

        Player player = requirePlayerWithUsePermission(sender);
        if (player == null) {
            return true;
        }
        if (!"fiyat".equals(subcommand)
                && !"al".equals(subcommand)
                && !"sat".equals(subcommand)) {
            sendUsage(player, command);
            return true;
        }
        if ("fiyat".equals(subcommand)) {
            showPrice(player, command, safeArgs);
        } else {
            trade(player, command, safeArgs,
                "al".equals(subcommand) ? TradeSide.BUY : TradeSide.SELL);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args == null) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            List<String> subcommands = new ArrayList<String>();
            if (sender.hasPermission(USE_PERMISSION)) {
                subcommands.add("fiyat");
                if (settings.komutTicaret) {
                    subcommands.add("al");
                    subcommands.add("sat");
                }
            }
            if (sender.hasPermission(ADMIN_PERMISSION)) {
                subcommands.add("admin");
            }
            return startsWith(subcommands, args[0]);
        }
        if (args.length >= 2 && "admin".equals(
                args[0].toLowerCase(Locale.ROOT))) {
            return completeAdmin(sender, args);
        }
        if (args.length == 2 && sender.hasPermission(USE_PERMISSION)
                && completesItems(args[0])) {
            return startsWith(completableItems(args[0]), args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> completeAdmin(CommandSender sender, String[] args) {
        if (adminDelegate == null || !settings.adminKomut
                || !sender.hasPermission(ADMIN_PERMISSION)) {
            return Collections.emptyList();
        }
        List<String> candidates = adminDelegate.complete(
            sender, Arrays.copyOfRange(args, 1, args.length));
        return candidates == null
            ? Collections.<String>emptyList()
            : startsWith(candidates, args[args.length - 1]);
    }

    private Player requirePlayerWithUsePermission(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.chat("komut.sadece-oyuncu"));
            return null;
        }
        if (!sender.hasPermission(USE_PERMISSION)) {
            sender.sendMessage(messages.chat("komut.yetki-yok"));
            return null;
        }
        return (Player) sender;
    }

    private void showPrice(Player player, Command command, String[] args) {
        if (args.length != 2) {
            sendUsage(player, command);
            return;
        }
        String itemId = resolveItemId(args[1]);
        ItemDef priced = itemId == null ? null : parsedItems.items.get(itemId);
        // Devre disi urun oyuncu icin "markette degil" gibi davranir.
        if (itemId == null || priced == null || !priced.active) {
            sendUnknownItem(player, args[1]);
            return;
        }

        try {
            ItemDef item = parsedItems.items.get(itemId);
            player.sendMessage(messages.chat(
                "gui.item-baslik", singletonVar(
                    "item", itemNames.of(itemId))));
            sendDirectionPrice(player, item, TradeSide.BUY);
            sendDirectionPrice(player, item, TradeSide.SELL);
        } catch (IllegalArgumentException ignored) {
            player.sendMessage(messages.chat("islem.hata"));
        }
    }

    private void trade(Player player, Command command, String[] args,
                       TradeSide side) {
        if (rejectDisabledCommandTrading(
                settings.komutTicaret, player, messages)) {
            return;
        }
        if (args.length != 3) {
            sendUsage(player, command);
            return;
        }
        String itemId = resolveItemId(args[1]);
        if (itemId == null) {
            sendUnknownItem(player, args[1]);
            return;
        }
        int amount = AmountParser.parse(
            args[2], settings.engineParams.maxTradeAmount);
        if (amount < 0) {
            player.sendMessage(messages.chat(
                "islem.gecersiz-adet", singletonVar("adet", args[2])));
            return;
        }

        MarketResult result = marketService.trade(player, itemId, amount, side);
        if (result == null || result.messageKey == null) {
            player.sendMessage(messages.chat("islem.hata"));
            return;
        }
        // Yurutme oncesi retlerde result.outcome null olabilir.
        player.sendMessage(messages.chat(result.messageKey, result.vars));
    }

    static boolean rejectDisabledCommandTrading(
            boolean commandTradingEnabled, Player player, Messages messages) {
        if (commandTradingEnabled) {
            return false;
        }
        player.sendMessage(messages.chat("komut.ozellik-kapali"));
        return true;
    }

    private void handleAdmin(CommandSender sender, Command command,
                             String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messages.chat("komut.yetki-yok"));
            return;
        }
        // /market admin (arguman yok) her zaman tiklanabilir menuyu acar;
        // GUI birincil yoldur, config kapisindan bagimsizdir.
        if (args.length == 1) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(messages.chat("komut.sadece-oyuncu"));
                return;
            }
            if (adminGuiHost != null) {
                AdminMenu.open((Player) sender, adminGuiHost);
            }
            return;
        }
        if (!settings.adminKomut) {
            sender.sendMessage(messages.chat("komut.admin-komut-kapali"));
            return;
        }
        if (adminDelegate == null || !adminDelegate.execute(
                sender, Arrays.copyOfRange(args, 1, args.length))) {
            sendUsage(sender, command);
        }
    }

    private boolean completesItems(String rawSubcommand) {
        String subcommand = rawSubcommand.toLowerCase(Locale.ROOT);
        return "fiyat".equals(subcommand)
            || settings.komutTicaret
                && ("al".equals(subcommand) || "sat".equals(subcommand));
    }

    private List<String> completableItems(String rawSubcommand) {
        String subcommand = rawSubcommand.toLowerCase(Locale.ROOT);
        TradeSide side = "al".equals(subcommand) ? TradeSide.BUY
            : "sat".equals(subcommand) ? TradeSide.SELL : null;
        List<String> itemIds = new ArrayList<String>();
        for (ItemDef item : parsedItems.items.values()) {
            // Devre disi urunler tamamlamada oyuncuya onerilmez.
            if (!item.active) {
                continue;
            }
            if (side == null || item.isTradeEnabled(side)) {
                itemIds.add(item.id);
            }
        }
        return itemIds;
    }

    private void sendDirectionPrice(Player player, ItemDef item,
                                    TradeSide side) {
        if (!item.isTradeEnabled(side)) {
            player.sendMessage(messages.chat(side == TradeSide.BUY
                ? "gui.alis-kapali" : "gui.satis-kapali"));
            return;
        }
        Quote quote = marketService.preview(player, item.id, 1, side);
        String label = messages.chat(
            side == TradeSide.BUY ? "gui.alis" : "gui.satis");
        player.sendMessage(label + " " + Icons.money(quote.unitAvg));
    }

    private String resolveItemId(String rawItem) {
        Material material = Materials.resolve(rawItem);
        if (material == null || !parsedItems.items.containsKey(material.name())) {
            return null;
        }
        return material.name();
    }

    private void sendUnknownItem(CommandSender sender, String rawItem) {
        sender.sendMessage(messages.chat(
            "islem.bilinmeyen-item", singletonVar(
                "item", itemNames.of(rawItem))));
    }

    private void sendUsage(CommandSender sender, Command command) {
        String usage = command == null ? "" : command.getUsage();
        sender.sendMessage(messages.chat(
            "komut.kullanim", singletonVar("kullanim", usage)));
    }

    private static Map<String, String> singletonVar(String key, String value) {
        Map<String, String> vars = new LinkedHashMap<String, String>();
        vars.put(key, value);
        return vars;
    }

    private static List<String> startsWith(List<String> candidates,
                                           String rawPrefix) {
        String prefix = rawPrefix == null
            ? "" : rawPrefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<String>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    public interface AdminDelegate {
        boolean execute(CommandSender sender, String[] args);

        /**
         * Admin alt komutlari icin ham aday listesi; onek suzmesini
         * {@link MarketCommand} yapar.
         *
         * @param args admin alt komutundan itibaren, son eleman yazilmakta
         *             olan token
         */
        List<String> complete(CommandSender sender, String[] args);
    }
}
