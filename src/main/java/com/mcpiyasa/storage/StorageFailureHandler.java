package com.mcpiyasa.storage;

import com.mcpiyasa.diag.SafeMode;

/** Ilk storage arizasini guvenli moda ve ana-thread admin bildirimine tasir. */
public final class StorageFailureHandler {
    public static final String REASON = "storage-write-failed";
    public static final String DETAIL =
        "Kalicilik yazimi basarisiz; disk ve veritabani erisimini kontrol edin.";

    /** Bukkit bagimliligini storage katmaninin disinda tutan ana-thread seam'i. */
    public interface MainThreadDispatcher {
        void dispatch(Runnable notification);
    }

    private final MainThreadDispatcher dispatcher;
    private final Runnable adminNotification;
    private SafeMode safeMode;
    private boolean failed;

    public StorageFailureHandler(SafeMode safeMode,
                                 MainThreadDispatcher dispatcher,
                                 Runnable adminNotification) {
        if (safeMode == null || dispatcher == null
                || adminNotification == null) {
            throw new IllegalArgumentException(
                "StorageFailureHandler bagimliliklari null olamaz");
        }
        this.safeMode = safeMode;
        this.dispatcher = dispatcher;
        this.adminNotification = adminNotification;
    }

    public void handle(Throwable failure) {
        if (failure == null) {
            throw new IllegalArgumentException("failure null olamaz");
        }
        boolean firstFailure;
        synchronized (this) {
            firstFailure = !failed;
            if (firstFailure) {
                failed = true;
                safeMode.activate(REASON, DETAIL);
            }
        }
        if (firstFailure) {
            dispatcher.dispatch(adminNotification);
        }
    }

    /** Reload yeni SafeMode nesnesi kurarsa storage ariza kilidini devreder. */
    public synchronized void setSafeMode(SafeMode safeMode) {
        if (safeMode == null) {
            throw new IllegalArgumentException("safeMode null olamaz");
        }
        this.safeMode = safeMode;
        if (failed) {
            safeMode.activate(REASON, DETAIL);
        }
    }

    public synchronized boolean hasFailed() {
        return failed;
    }
}
