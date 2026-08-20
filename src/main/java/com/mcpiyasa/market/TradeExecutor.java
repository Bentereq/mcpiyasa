package com.mcpiyasa.market;

import com.mcpiyasa.engine.Quote;
import com.mcpiyasa.engine.TradeSide;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Iki dis bridge arasindaki transferi telafili durum makineleriyle yurutur. */
public final class TradeExecutor {
    private final Logger logger;

    public TradeExecutor(Logger logger) {
        if (logger == null) {
            throw new IllegalArgumentException("logger null olamaz");
        }
        this.logger = logger;
    }

    public TradeOutcome execute(UUID player, Quote quote,
                                EconomyBridge economy,
                                InventoryBridge inventory) {
        if (quote == null || economy == null || inventory == null
                || quote.amount <= 0) {
            throw new IllegalArgumentException(
                "quote, economy ve inventory gecerli olmalidir");
        }

        if (quote.side == TradeSide.SELL) {
            return executeSell(player, quote, economy, inventory);
        }
        if (quote.side == TradeSide.BUY) {
            return executeBuy(player, quote, economy, inventory);
        }
        throw new IllegalArgumentException("quote.side BUY veya SELL olmalidir");
    }

    private TradeOutcome executeSell(UUID player, Quote quote,
                                     EconomyBridge economy,
                                     InventoryBridge inventory) {
        String phase = "sell-count";
        try {
            if (inventory.count(player, quote.itemId) < quote.amount) {
                return TradeOutcome.YETERSIZ_ITEM;
            }
            phase = "sell-remove";
            if (!inventory.remove(player, quote.itemId, quote.amount)) {
                return TradeOutcome.YETERSIZ_ITEM;
            }
        } catch (RuntimeException | LinkageError failure) {
            logBridgeFailure(player, quote, phase, failure);
            return TradeOutcome.EKONOMI_HATASI;
        }

        try {
            if (economy.deposit(player, quote.totalPrice)) {
                return TradeOutcome.OK;
            }
        } catch (RuntimeException | LinkageError failure) {
            logBridgeFailure(player, quote, "sell-deposit", failure);
        }
        return compensateSell(player, quote, inventory);
    }

    private TradeOutcome compensateSell(UUID player, Quote quote,
                                        InventoryBridge inventory) {
        try {
            if (inventory.add(player, quote.itemId, quote.amount)) {
                return TradeOutcome.EKONOMI_HATASI;
            }
            logRollbackFailure(player, quote, "sell-inventory-add", null);
        } catch (RuntimeException | LinkageError failure) {
            logRollbackFailure(
                player, quote, "sell-inventory-add", failure);
        }
        return TradeOutcome.ROLLBACK_HATASI;
    }

    private TradeOutcome executeBuy(UUID player, Quote quote,
                                    EconomyBridge economy,
                                    InventoryBridge inventory) {
        String phase = "buy-free-capacity";
        try {
            if (inventory.freeCapacity(player, quote.itemId) < quote.amount) {
                return TradeOutcome.ENVANTER_DOLU;
            }
            phase = "buy-has";
            if (!economy.has(player, quote.totalPrice)) {
                return TradeOutcome.YETERSIZ_BAKIYE;
            }
            phase = "buy-withdraw";
            if (!economy.withdraw(player, quote.totalPrice)) {
                return TradeOutcome.EKONOMI_HATASI;
            }
        } catch (RuntimeException | LinkageError failure) {
            logBridgeFailure(player, quote, phase, failure);
            return TradeOutcome.EKONOMI_HATASI;
        }

        try {
            if (inventory.add(player, quote.itemId, quote.amount)) {
                return TradeOutcome.OK;
            }
        } catch (RuntimeException | LinkageError failure) {
            logBridgeFailure(player, quote, "buy-inventory-add", failure);
        }
        return compensateBuy(player, quote, economy);
    }

    private TradeOutcome compensateBuy(UUID player, Quote quote,
                                       EconomyBridge economy) {
        try {
            if (economy.deposit(player, quote.totalPrice)) {
                return TradeOutcome.EKONOMI_HATASI;
            }
            logRollbackFailure(player, quote, "buy-economy-deposit", null);
        } catch (RuntimeException | LinkageError failure) {
            logRollbackFailure(player, quote, "buy-economy-deposit", failure);
        }
        return TradeOutcome.ROLLBACK_HATASI;
    }

    private void logBridgeFailure(UUID player, Quote quote,
                                  String phase, Throwable failure) {
        logger.log(
            Level.WARNING,
            context("Bridge cagrisi basarisiz", player, quote, phase),
            failure);
    }

    private void logRollbackFailure(UUID player, Quote quote,
                                    String phase, Throwable failure) {
        String message = context(
            "ROLLBACK BASARISIZ - manuel mudahale gerekebilir",
            player,
            quote,
            phase);
        if (failure == null) {
            logger.severe(message);
        } else {
            logger.log(Level.SEVERE, message, failure);
        }
    }

    private static String context(String message, UUID player,
                                  Quote quote, String phase) {
        return message
            + " player=" + player
            + " itemId=" + quote.itemId
            + " amount=" + quote.amount
            + " side=" + quote.side
            + " phase=" + phase;
    }
}
