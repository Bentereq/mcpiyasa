package com.mcpiyasa.storage;

import com.mcpiyasa.market.TradeRecord;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Basarili ve basarisiz piyasa islemlerini kaydeder. */
public final class TxLogRepo {
    private static final String INSERT_SQL =
        "INSERT INTO tx_log("
            + "time_ms, player, item_id, group_id, amount, side, total, weight, result"
            + ") VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String DAILY_VOLUME_FOR_ITEM_SQL =
        "SELECT COALESCE(SUM(amount), 0.0) AS volume "
            + "FROM tx_log "
            + "WHERE item_id=? AND time_ms>=? AND result='OK'";

    private final Db db;

    public TxLogRepo(Db db) {
        this.db = db;
    }

    public void insert(TradeRecord record) {
        synchronized (db.lock) {
            if (record.player == null) {
                throw new IllegalArgumentException("Oyuncu UUID degeri null olamaz");
            }
            try (PreparedStatement statement = db.connection().prepareStatement(INSERT_SQL)) {
                statement.setLong(1, record.timeMs);
                statement.setString(2, record.player.toString());
                statement.setString(3, record.itemId);
                statement.setString(4, record.groupId);
                statement.setInt(5, record.amount);
                statement.setString(6, record.side);
                statement.setDouble(7, record.totalPrice);
                statement.setDouble(8, record.weight);
                statement.setString(9, record.result);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Islem kaydi yazilamadi", e);
            }
        }
    }

    /**
     * Verilen item'in belirtilen andan sonraki basarili BUY ve SELL adetlerini
     * agirlik uygulamadan toplar.
     */
    public double dailyVolumeForItem(String itemId, long sinceMs) {
        synchronized (db.lock) {
            try (PreparedStatement statement =
                     db.connection().prepareStatement(DAILY_VOLUME_FOR_ITEM_SQL)) {
                statement.setString(1, itemId);
                statement.setLong(2, sinceMs);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getDouble("volume") : 0.0;
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Gunluk item hacmi okunamadi", e);
            }
        }
    }
}
