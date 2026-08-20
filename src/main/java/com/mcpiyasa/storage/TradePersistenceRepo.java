package com.mcpiyasa.storage;

import com.mcpiyasa.market.TradeRecord;

import java.sql.Connection;
import java.sql.SQLException;

/** Basarili islem kaydi ile motor durumunu tek SQLite transaction'inda yazar. */
public final class TradePersistenceRepo {
    interface TxLogWriter {
        void insert(TradeRecord record);
    }

    interface StateWriter {
        void save(String groupId, double stock, double epsilon);
    }

    private final Db db;
    private final TxLogWriter txLogWriter;
    private final StateWriter stateWriter;

    public TradePersistenceRepo(Db db,
                                final TxLogRepo txLogRepo,
                                final StateRepo stateRepo) {
        this(
            db,
            new TxLogWriter() {
                @Override
                public void insert(TradeRecord record) {
                    txLogRepo.insert(record);
                }
            },
            new StateWriter() {
                @Override
                public void save(String groupId,
                                 double stock,
                                 double epsilon) {
                    stateRepo.save(groupId, stock, epsilon);
                }
            });
        if (txLogRepo == null || stateRepo == null) {
            throw new IllegalArgumentException(
                "txLogRepo ve stateRepo null olamaz");
        }
    }

    TradePersistenceRepo(Db db,
                         TxLogWriter txLogWriter,
                         StateWriter stateWriter) {
        if (db == null || txLogWriter == null || stateWriter == null) {
            throw new IllegalArgumentException(
                "TradePersistenceRepo bagimliliklari null olamaz");
        }
        this.db = db;
        this.txLogWriter = txLogWriter;
        this.stateWriter = stateWriter;
    }

    public void persistSuccess(TradeRecord record,
                               String groupId,
                               double stock,
                               double epsilon) {
        synchronized (db.lock) {
            Connection connection = db.connection();
            boolean transactionStarted = false;
            Throwable failure = null;
            try {
                if (!connection.getAutoCommit()) {
                    throw new IllegalStateException(
                        "Trade persistence mevcut transaction icinde calisamaz");
                }
                connection.setAutoCommit(false);
                transactionStarted = true;
                txLogWriter.insert(record);
                stateWriter.save(groupId, stock, epsilon);
                connection.commit();
            } catch (SQLException sqlFailure) {
                failure = sqlFailure;
                rollback(connection, transactionStarted, sqlFailure);
                throw new IllegalStateException(
                    "Basarili islem transaction'i kaydedilemedi", sqlFailure);
            } catch (RuntimeException | LinkageError runtimeFailure) {
                failure = runtimeFailure;
                rollback(connection, transactionStarted, runtimeFailure);
                throw runtimeFailure;
            } finally {
                restoreAutoCommit(connection, transactionStarted, failure);
            }
        }
    }

    private static void rollback(Connection connection,
                                 boolean transactionStarted,
                                 Throwable cause) {
        if (!transactionStarted) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            cause.addSuppressed(rollbackFailure);
        }
    }

    private static void restoreAutoCommit(Connection connection,
                                          boolean transactionStarted,
                                          Throwable cause) {
        if (!transactionStarted) {
            return;
        }
        try {
            connection.setAutoCommit(true);
        } catch (SQLException restoreFailure) {
            if (cause != null) {
                cause.addSuppressed(restoreFailure);
            } else {
                throw new IllegalStateException(
                    "SQLite otomatik commit modu geri yuklenemedi",
                    restoreFailure);
            }
        }
    }
}
