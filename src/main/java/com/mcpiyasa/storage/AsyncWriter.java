package com.mcpiyasa.storage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Storage islerini tek bir sirali arka plan thread'inde calistirir. */
public final class AsyncWriter {
    private static final long SHUTDOWN_GRACE_MS = 1000L;

    private final ExecutorService executor;
    private final Logger logger;
    private final StorageFailureHandler failureHandler;

    public AsyncWriter(Logger logger, StorageFailureHandler failureHandler) {
        if (logger == null || failureHandler == null) {
            throw new IllegalArgumentException(
                "logger ve failureHandler null olamaz");
        }
        this.logger = logger;
        this.failureHandler = failureHandler;
        executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(
                    runnable, "mcpiyasa-storage-writer");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public void submit(final Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("task null olamaz");
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } catch (RuntimeException | LinkageError failure) {
                    logger.log(
                        Level.SEVERE,
                        "Asenkron storage islemi basarisiz oldu",
                        failure);
                    try {
                        failureHandler.handle(failure);
                    } catch (RuntimeException | LinkageError handlerFailure) {
                        failure.addSuppressed(handlerFailure);
                        logger.log(
                            Level.SEVERE,
                            "Storage hata bildirimi basarisiz oldu",
                            handlerFailure);
                    }
                }
            }
        });
    }

    /** Daha once siralanan tum yazilarin tamamlanmasini bekler. */
    public boolean awaitIdle(long timeoutMs) {
        if (timeoutMs < 0L) {
            throw new IllegalArgumentException("timeoutMs negatif olamaz");
        }
        Future<?> barrier;
        try {
            barrier = executor.submit(new Runnable() {
                @Override public void run() { }
            });
        } catch (RuntimeException failure) {
            return false;
        }
        try {
            barrier.get(timeoutMs, TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException e) {
            return false;
        }
    }

    public boolean closeAndFlush(long timeoutMs) {
        if (timeoutMs < 0L) {
            throw new IllegalArgumentException("timeoutMs negatif olamaz");
        }

        executor.shutdown();
        try {
            if (executor.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)) {
                return true;
            }
            executor.shutdownNow();
            executor.awaitTermination(
                SHUTDOWN_GRACE_MS, TimeUnit.MILLISECONDS);
            return false;
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
