package com.mcpiyasa.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Motorun kuresel meta degerlerini saklar (orn. {@code hassasiyet}).
 *
 * <p>Kalici stok, yazildigi anki {@code hassasiyet} degerine gore
 * olceklidir (baseStock = configBaseStock/hassasiyet). Bu deger burada
 * saklanir; geri yukleme sirasinda kayitli hassasiyet ile mevcut hassasiyet
 * farkliysa stok yeniden olceklenir, boylece fiyat korunur.
 */
public final class EngineMetaRepo {
    private static final String KEY_HASSASIYET = "hassasiyet";
    private static final String SAVE_SQL =
        "INSERT OR REPLACE INTO engine_meta(meta_key, meta_value) VALUES(?, ?)";
    private static final String LOAD_SQL =
        "SELECT meta_value FROM engine_meta WHERE meta_key=?";

    private final Db db;

    public EngineMetaRepo(Db db) {
        this.db = db;
    }

    public void saveHassasiyet(double value) {
        synchronized (db.lock) {
            try (PreparedStatement statement =
                    db.connection().prepareStatement(SAVE_SQL)) {
                statement.setString(1, KEY_HASSASIYET);
                statement.setDouble(2, value);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException(
                    "Hassasiyet meta degeri kaydedilemedi", e);
            }
        }
    }

    /** Hic kayit yoksa (temiz DB / yukseltme) {@code null} doner. */
    public Double loadHassasiyet() {
        synchronized (db.lock) {
            try (PreparedStatement statement =
                    db.connection().prepareStatement(LOAD_SQL)) {
                statement.setString(1, KEY_HASSASIYET);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        return Double.valueOf(result.getDouble("meta_value"));
                    }
                    return null;
                }
            } catch (SQLException e) {
                throw new IllegalStateException(
                    "Hassasiyet meta degeri okunamadi", e);
            }
        }
    }
}
