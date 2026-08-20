package com.mcpiyasa.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Haftanin 168 saatlik hacim profilini saklar. */
public final class ProfileRepo {
    private static final int SLOT_COUNT = 168;
    private static final String SAVE_SQL =
        "INSERT OR REPLACE INTO volume_profile(group_id, slot, ema, cnt) VALUES(?, ?, ?, ?)";
    private static final String LOAD_EMA_SQL =
        "SELECT slot, ema FROM volume_profile WHERE group_id=?";
    private static final String LOAD_COUNT_SQL =
        "SELECT slot, cnt FROM volume_profile WHERE group_id=?";

    private final Db db;
    private final Logger logger;

    public ProfileRepo(Db db, Logger logger) {
        if (db == null || logger == null) {
            throw new IllegalArgumentException("db ve logger null olamaz");
        }
        this.db = db;
        this.logger = logger;
    }

    public void save(String groupId, double[] ema, int[] count) {
        synchronized (db.lock) {
            requireProfileLengths(ema, count);
            Connection connection = db.connection();
            boolean managesTransaction = false;
            SQLException failure = null;
            try {
                managesTransaction = connection.getAutoCommit();
                if (managesTransaction) {
                    connection.setAutoCommit(false);
                }
                try (PreparedStatement statement = connection.prepareStatement(SAVE_SQL)) {
                    for (int slot = 0; slot < SLOT_COUNT; slot++) {
                        statement.setString(1, groupId);
                        statement.setInt(2, slot);
                        statement.setDouble(3, ema[slot]);
                        statement.setInt(4, count[slot]);
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                if (managesTransaction) {
                    connection.commit();
                }
            } catch (SQLException e) {
                failure = e;
                rollback(connection, managesTransaction, e);
                throw new IllegalStateException("Hacim profili kaydedilemedi", e);
            } finally {
                restoreAutoCommit(connection, managesTransaction, failure);
            }
        }
    }

    public double[] loadEma(String groupId) {
        synchronized (db.lock) {
            double[] values = new double[SLOT_COUNT];
            boolean found = false;
            try (PreparedStatement statement = db.connection().prepareStatement(LOAD_EMA_SQL)) {
                statement.setString(1, groupId);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        int slot = result.getInt("slot");
                        if (slot >= 0 && slot < SLOT_COUNT) {
                            values[slot] = result.getDouble("ema");
                            found = true;
                        }
                    }
                }
                return found ? values : null;
            } catch (SQLException e) {
                throw new IllegalStateException("Hacim profili EMA degerleri okunamadi", e);
            }
        }
    }

    public int[] loadCount(String groupId) {
        synchronized (db.lock) {
            int[] values = new int[SLOT_COUNT];
            boolean found = false;
            try (PreparedStatement statement = db.connection().prepareStatement(LOAD_COUNT_SQL)) {
                statement.setString(1, groupId);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        int slot = result.getInt("slot");
                        if (slot >= 0 && slot < SLOT_COUNT) {
                            values[slot] = result.getInt("cnt");
                            found = true;
                        }
                    }
                }
                return found ? values : null;
            } catch (SQLException e) {
                throw new IllegalStateException("Hacim profili sayaclari okunamadi", e);
            }
        }
    }

    private static void requireProfileLengths(double[] ema, int[] count) {
        if (ema == null || count == null || ema.length != SLOT_COUNT || count.length != SLOT_COUNT) {
            throw new IllegalArgumentException("Hacim profili dizileri 168 elemanli olmalidir");
        }
    }

    private static void rollback(
        Connection connection,
        boolean managesTransaction,
        SQLException cause
    ) {
        if (!managesTransaction) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException rollbackError) {
            cause.addSuppressed(rollbackError);
        }
    }

    private void restoreAutoCommit(
        Connection connection,
        boolean managesTransaction,
        SQLException cause
    ) {
        if (!managesTransaction) {
            return;
        }
        try {
            connection.setAutoCommit(true);
        } catch (SQLException restoreError) {
            if (cause != null) {
                cause.addSuppressed(restoreError);
            } else {
                logger.log(
                    Level.WARNING,
                    "SQLite otomatik commit modu geri yuklenemedi",
                    restoreError
                );
            }
        }
    }
}
