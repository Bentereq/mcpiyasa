package com.mcpiyasa.diag;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Bukkit durumundan acilis kontrollerini derleyen ince teshis katmani. */
public final class Diagnostics {
    private static final String FRAME =
            "============================================================";

    /** Teşhis metinlerini etkin dil dosyasından alan küçük lookup yüzeyi. */
    public interface MessageLookup {
        String get(String key);
    }

    private final MessageLookup messages;

    /**
     * Enjekte edilen mesaj servisiyle bir teshis calistiricisi olusturur.
     *
     * @throws IllegalArgumentException mesaj servisi {@code null} ise
     */
    public Diagnostics(MessageLookup messages) {
        if (messages == null) {
            throw new IllegalArgumentException("messages");
        }
        this.messages = messages;
    }

    /**
     * Tum acilis kontrollerini calistirir ve basarisiz olanlari konsola basar.
     * Teshis mesajlarinin her biri sebebi ve owner icin cozum onerisi icermelidir.
     */
    public static List<DiagCheck> runAll(Plugin plugin, File databaseFile,
                                          int loadedItemCount, MessageLookup messages) {
        return new Diagnostics(messages).runAll(plugin, databaseFile, loadedItemCount);
    }

    /** Tum acilis kontrollerini enjekte edilen mesaj servisiyle calistirir. */
    public List<DiagCheck> runAll(Plugin plugin, File databaseFile,
                                  int loadedItemCount) {
        Server server = plugin.getServer();
        Logger logger = loggerOf(plugin);
        boolean economyOk = false;
        try {
            RegisteredServiceProvider<Economy> registration =
                    server.getServicesManager().getRegistration(Economy.class);
            economyOk = registration != null && registration.getProvider() != null;
        } catch (LinkageError ignored) {
            economyOk = false;
        }

        PluginCommand marketCommand = server.getPluginCommand("market");
        List<DiagCheck> checks = new ArrayList<DiagCheck>();
        checks.add(new DiagCheck("economy", economyOk, "diag.ekonomi-yok"));
        checks.add(new DiagCheck("database", probeWrite(databaseFile, logger),
                "diag.db-yazilamiyor"));
        checks.add(new DiagCheck("items", loadedItemCount >= 1, "diag.items-yok"));
        checks.add(new DiagCheck("market-command",
                marketCommand != null && marketCommand.getPlugin() == plugin,
                "diag.market-komut-cakismasi"));

        renderWarningBlock(logger, checks);
        return checks;
    }

    /** Basarisiz kontrolleri tek, belirgin bir konsol uyarisi olarak render eder. */
    public static void renderWarningBlock(Logger logger, List<DiagCheck> checks,
                                           MessageLookup messages) {
        new Diagnostics(messages).renderWarningBlock(logger, checks);
    }

    /** Basarisiz kontrolleri enjekte edilen mesaj servisiyle render eder. */
    public void renderWarningBlock(Logger logger, List<DiagCheck> checks) {
        Logger effectiveLogger = logger == null
                ? Logger.getLogger(Diagnostics.class.getName()) : logger;
        boolean hasFailure = false;
        for (DiagCheck check : checks) {
            if (!check.ok) {
                if (!hasFailure) {
                    effectiveLogger.warning(FRAME);
                    effectiveLogger.warning(messages.get("diag.acilis-uyarisi"));
                    hasFailure = true;
                }
                effectiveLogger.warning(check.id + ": " + messages.get(check.messageKey));
            }
        }
        if (hasFailure) {
            effectiveLogger.warning(FRAME);
        }
    }

    private static Logger loggerOf(Plugin plugin) {
        Logger logger = plugin.getLogger();
        return logger == null ? Logger.getLogger(Diagnostics.class.getName()) : logger;
    }

    private static boolean probeWrite(File file, Logger logger) {
        if (file == null) {
            return false;
        }
        if (file.exists() && !file.canWrite()) {
            return false;
        }
        File probe = null;
        try {
            File parent = file.getAbsoluteFile().getParentFile();
            if (parent == null) {
                throw new IOException("Database parent directory is unavailable");
            }
            if (!parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
                throw new IOException("Cannot create database parent directory: " + parent);
            }

            // SQLite DB dosyasina dokunmak WAL durumunu etkileyip bozulma riski yaratabilir.
            probe = File.createTempFile("mcpiyasa-probe-", ".tmp", parent);
            if (!probe.delete()) {
                probe.deleteOnExit();
                throw new IOException("Cannot delete database directory probe: " + probe);
            }
            return true;
        } catch (IOException failure) {
            logger.log(Level.WARNING, "Database directory write probe failed.", failure);
            return false;
        } catch (SecurityException failure) {
            logger.log(Level.WARNING, "Database directory write probe failed.", failure);
            return false;
        }
    }
}
