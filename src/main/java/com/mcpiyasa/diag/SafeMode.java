package com.mcpiyasa.diag;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Ticareti teshis ve ardisik islem hatalarina gore kilitleyen saf durum nesnesi.
 * Ana ve async thread'lerden kullanilabilecegi icin tum erisimler senkronizedir.
 */
public final class SafeMode {
    private static final long ERROR_WINDOW_MS = 300_000L;
    private static final int ERROR_LIMIT = 3;
    public static final String TRADE_ERROR_REASON = "trade-error-limit";
    private static final String TRADE_ERROR_DETAIL =
        "Bes dakika icinde uc ekonomi veya rollback hatasi olustu; "
            + "Vault ve envanter entegrasyonlarini kontrol edin.";

    private final Deque<Long> tradeErrors = new ArrayDeque<Long>();
    private boolean active;
    private String reason = "";
    private String detail = "";
    private long lastSeenNowMs = Long.MIN_VALUE;

    public synchronized boolean isActive() {
        return active;
    }

    public synchronized String reason() {
        return reason;
    }

    public synchronized String detail() {
        return detail;
    }

    /** Ilk pasif->aktif gecisinde true dondurur. */
    public synchronized boolean activate(String reasonCode) {
        return activate(reasonCode, "");
    }

    /** Ilk pasif->aktif gecisinde true dondurur; en yeni nedeni tasir. */
    public synchronized boolean activate(String reasonCode, String safeDetail) {
        boolean firstActivation = !active;
        active = true;
        reason = sanitize(reasonCode, 96);
        detail = sanitize(safeDetail, 512);
        return firstActivation;
    }

    public synchronized boolean tradingAllowed(boolean zorlaCalistir) {
        return !active || zorlaCalistir;
    }

    /** Geri giden saat degerlerini son gorulen zamana kelepceleyerek sirayi korur. */
    public synchronized boolean reportTradeError(long nowMs) {
        return reportTradeError(nowMs, TRADE_ERROR_DETAIL);
    }

    /** Hata limiti ilk kez safe mode'u acarsa true dondurur. */
    public synchronized boolean reportTradeError(long nowMs, String safeDetail) {
        long effectiveNowMs = Math.max(nowMs, lastSeenNowMs);
        lastSeenNowMs = effectiveNowMs;
        while (!tradeErrors.isEmpty()
                && effectiveNowMs - tradeErrors.peekFirst().longValue() > ERROR_WINDOW_MS) {
            tradeErrors.removeFirst();
        }
        tradeErrors.addLast(Long.valueOf(effectiveNowMs));
        if (tradeErrors.size() >= ERROR_LIMIT) {
            return activate(TRADE_ERROR_REASON, safeDetail);
        }
        return false;
    }

    public synchronized void reportTradeSuccess() {
        tradeErrors.clear();
    }

    private static String sanitize(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        StringBuilder safe = new StringBuilder();
        for (int i = 0; i < value.length() && safe.length() < maxLength; i++) {
            char ch = value.charAt(i);
            safe.append(Character.isISOControl(ch) ? ' ' : ch);
        }
        return safe.toString().trim();
    }
}
