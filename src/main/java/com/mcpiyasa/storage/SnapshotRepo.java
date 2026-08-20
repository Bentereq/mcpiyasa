package com.mcpiyasa.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Gunluk orta fiyat anlik goruntulerini saklar. */
public final class SnapshotRepo {
    private static final String SAVE_SQL =
        "INSERT OR REPLACE INTO daily_snapshot(date_key, item_id, mid) VALUES(?, ?, ?)";
    private static final String LAST_BEFORE_SQL =
        "SELECT mid FROM daily_snapshot "
            + "WHERE item_id=? AND date_key<? "
            + "ORDER BY date_key DESC LIMIT 1";
    private static final String LAST_BEFORE_ALL_SQL =
        "SELECT snapshot.item_id, snapshot.mid "
            + "FROM daily_snapshot snapshot "
            + "JOIN ("
            + "SELECT item_id, MAX(date_key) AS date_key "
            + "FROM daily_snapshot WHERE date_key<? GROUP BY item_id"
            + ") latest ON latest.item_id=snapshot.item_id "
            + "AND latest.date_key=snapshot.date_key "
            + "ORDER BY snapshot.item_id";

    private final Db db;

    public SnapshotRepo(Db db) {
        this.db = db;
    }

    public void saveDaily(String dateKey, String itemId, double mid) {
        synchronized (db.lock) {
            try (PreparedStatement statement = db.connection().prepareStatement(SAVE_SQL)) {
                statement.setString(1, dateKey);
                statement.setString(2, itemId);
                statement.setDouble(3, mid);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Gunluk fiyat kaydedilemedi", e);
            }
        }
    }

    public double lastBefore(String itemId, String dateKey) {
        synchronized (db.lock) {
            try (PreparedStatement statement = db.connection().prepareStatement(LAST_BEFORE_SQL)) {
                statement.setString(1, itemId);
                statement.setString(2, dateKey);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getDouble("mid") : -1.0;
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Onceki gunluk fiyat okunamadi", e);
            }
        }
    }

    public Map<String, Double> lastBeforeAll(String dateKey) {
        synchronized (db.lock) {
            Map<String, Double> snapshots = new LinkedHashMap<String, Double>();
            try (PreparedStatement statement =
                     db.connection().prepareStatement(LAST_BEFORE_ALL_SQL)) {
                statement.setString(1, dateKey);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        snapshots.put(
                            result.getString("item_id"),
                            result.getDouble("mid"));
                    }
                }
                return snapshots;
            } catch (SQLException e) {
                throw new IllegalStateException(
                    "Onceki gunluk fiyatlar okunamadi", e);
            }
        }
    }
}
