package com.mcpiyasa.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Grup stok ve epsilon durumunu saklar. */
public final class StateRepo {
    private static final String SAVE_SQL =
        "INSERT OR REPLACE INTO item_state(group_id, stock, epsilon) VALUES(?, ?, ?)";
    private static final String LOAD_ALL_SQL =
        "SELECT group_id, stock, epsilon FROM item_state";

    private final Db db;

    public StateRepo(Db db) {
        this.db = db;
    }

    public void save(String groupId, double stock, double epsilon) {
        synchronized (db.lock) {
            try (PreparedStatement statement = db.connection().prepareStatement(SAVE_SQL)) {
                statement.setString(1, groupId);
                statement.setDouble(2, stock);
                statement.setDouble(3, epsilon);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Grup durumu kaydedilemedi", e);
            }
        }
    }

    public Map<String, double[]> loadAll() {
        synchronized (db.lock) {
            Map<String, double[]> states = new LinkedHashMap<String, double[]>();
            try (PreparedStatement statement = db.connection().prepareStatement(LOAD_ALL_SQL);
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    states.put(
                        result.getString("group_id"),
                        new double[] {result.getDouble("stock"), result.getDouble("epsilon")}
                    );
                }
                return states;
            } catch (SQLException e) {
                throw new IllegalStateException("Grup durumlari okunamadi", e);
            }
        }
    }
}
