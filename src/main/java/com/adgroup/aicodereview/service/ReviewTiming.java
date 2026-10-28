package com.adgroup.aicodereview.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public final class ReviewTiming {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReviewTiming.class);
    private static final String CORRELATION_ID_KEY = "reviewCorrelationId";

    private ReviewTiming() {
    }

    public static String correlationId() {
        String correlationId = MDC.get(CORRELATION_ID_KEY);
        return correlationId == null ? "review-" + UUID.randomUUID() : correlationId;
    }

    public static String setCorrelationId(String correlationId) {
        String previous = MDC.get(CORRELATION_ID_KEY);
        MDC.put(CORRELATION_ID_KEY, correlationId);
        return previous;
    }

    public static void restoreCorrelationId(String previous) {
        if (previous == null) {
            MDC.remove(CORRELATION_ID_KEY);
        } else {
            MDC.put(CORRELATION_ID_KEY, previous);
        }
    }

    public static <T> CompletableFuture<T> supplyAsync(
            Executor executor,
            String stage,
            Supplier<T> operation) {
        String correlationId = correlationId();
        long submittedNanos = System.nanoTime();
        return CompletableFuture.supplyAsync(() -> {
            String previous = setCorrelationId(correlationId);
            try {
                long queueDelayMillis = (System.nanoTime() - submittedNanos) / 1_000_000;
                LOGGER.info(
                        "Review timing: correlationId={} stage={} queueDelayMs={}",
                        correlationId, stage, queueDelayMillis);
                return measure(stage, operation);
            } finally {
                restoreCorrelationId(previous);
            }
        }, executor);
    }

    public static <T> T measure(String stage, Supplier<T> operation) {
        return measureChecked(stage, operation::get);
    }

    public static <T, E extends Exception> T measureChecked(
            String stage,
            CheckedSupplier<T, E> operation) throws E {
        String correlationId = correlationId();
        long startNanos = System.nanoTime();
        boolean succeeded = false;
        try {
            T result = operation.get();
            succeeded = true;
            return result;
        } finally {
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
            LOGGER.info(
                    "Review timing: correlationId={} stage={} durationMs={} outcome={}",
                    correlationId, stage, elapsedMillis, succeeded ? "success" : "failure");
        }
    }

    @FunctionalInterface
    public interface CheckedSupplier<T, E extends Exception> {
        T get() throws E;
    }
}
