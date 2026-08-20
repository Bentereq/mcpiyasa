package com.mcpiyasa.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

/** SQLite baglantisini ve MCPiyasa semasini yonetir. */
public final class Db implements AutoCloseable {
    public final Object lock = new Object();
    private final Connection connection;
    private final Logger logger;

    public Db(File file, Logger logger) {
        if (file == null || logger == null) {
            throw new IllegalArgumentException("file ve logger null olamaz");
        }
        this.logger = logger;
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        Connection opened = null;
        try {
            opened = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            initialize(opened);
            this.connection = opened;
        } catch (SQLException e) {
            closeAfterFailedOpen(opened, e);
            throw new IllegalStateException("SQLite veritabani acilamadi", e);
        }
    }

    Connection connection() {
        return connection;
    }

    private static void initialize(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute(
                "CREATE TABLE IF NOT EXISTS item_state ("
                    + "group_id TEXT PRIMARY KEY, "
                    + "stock REAL, "
                    + "epsilon REAL)"
            );
            statement.execute(
                "CREATE TABLE IF NOT EXISTS tx_log ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "time_ms INTEGER, "
                    + "player TEXT, "
                    + "item_id TEXT, "
                    + "group_id TEXT, "
                    + "amount INTEGER, "
                    + "side TEXT, "
                    + "total REAL, "
                    + "weight REAL, "
                    + "result TEXT)"
            );
            statement.execute(
                "CREATE INDEX IF NOT EXISTS idx_tx_log_group_time "
                    + "ON tx_log(group_id, time_ms)"
            );
            statement.execute(
                "CREATE INDEX IF NOT EXISTS idx_tx_log_item_time "
                    + "ON tx_log(item_id, time_ms)"
            );
            statement.execute(
                "CREATE TABLE IF NOT EXISTS volume_profile ("
                    + "group_id TEXT, "
                    + "slot INTEGER, "
                    + "ema REAL, "
                    + "cnt INTEGER, "
                    + "PRIMARY KEY(group_id, slot))"
            );
            statement.execute(
                "CREATE TABLE IF NOT EXISTS daily_snapshot ("
                    + "date_key TEXT, "
                    + "item_id TEXT, "
                    + "mid REAL, "
                    + "PRIMARY KEY(date_key, item_id))"
            );
            statement.execute(
                "CREATE INDEX IF NOT EXISTS idx_daily_snapshot_item_date "
                    + "ON daily_snapshot(item_id, date_key)"
            );
            statement.execute(
                "CREATE TABLE IF NOT EXISTS engine_meta ("
                    + "meta_key TEXT PRIMARY KEY, "
                    + "meta_value REAL)"
            );
        }
    }

    private static void closeAfterFailedOpen(Connection connection, SQLException cause) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException closeError) {
            cause.addSuppressed(closeError);
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            try {
                connection.close();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "SQLite veritabani kapatilamadi", e);
            }
        }
    }
}
