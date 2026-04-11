package me.sunmc.db.api.resilience;

import me.sunmc.db.util.SunDBLogger;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Three-state circuit breaker: CLOSED → OPEN → HALF_OPEN → CLOSED.
 * <p>
 * CLOSED: Normal operation. Failures are counted.
 * OPEN: DB appears down. All calls fail immediately (fast-fail).
 * No real DB connections are attempted, protecting the pool.
 * HALF_OPEN: Timeout expired. One probe attempt is allowed through.
 * Success → CLOSED. Failure → back to OPEN.
 */
public final class CircuitBreaker {

    public enum State {CLOSED, OPEN, HALF_OPEN}

    private final String dbName;
    private final int failureThreshold;
    private final Duration timeout;

    private final AtomicInteger failures = new AtomicInteger(0);
    private volatile State state = State.CLOSED;
    private volatile Instant openedAt = Instant.MIN;

    public CircuitBreaker(String dbName, int failureThreshold, Duration timeout) {
        this.dbName = dbName;
        this.failureThreshold = failureThreshold;
        this.timeout = timeout;
    }

    /**
     * Execute an operation through the circuit breaker.
     * Throws {@link CircuitOpenException} immediately if the circuit is OPEN
     * and the timeout has not yet elapsed.
     */
    public <T> CompletableFuture<T> execute(Supplier<CompletableFuture<T>> operation) {
        switch (state) {
            case OPEN -> {
                if (Duration.between(openedAt, Instant.now()).compareTo(timeout) >= 0) {
                    state = State.HALF_OPEN;
                    SunDBLogger.info("[Circuit:" + dbName + "] Entering HALF_OPEN — probing DB...");
                } else {
                    long secsLeft = Duration.between(Instant.now(), openedAt.plus(timeout)).toSeconds();
                    return CompletableFuture.failedFuture(new CircuitOpenException(
                            "Database '" + dbName + "' is temporarily unavailable " +
                                    "(circuit OPEN — retry in " + secsLeft + "s). " +
                                    "Consider showing the player a friendly error message."));
                }
            }
            case HALF_OPEN -> SunDBLogger.info("[Circuit:" + dbName + "] HALF_OPEN probe in progress.");
        }

        return operation.get().whenComplete((result, ex) -> {
            if (ex != null) {
                int count = failures.incrementAndGet();
                if (count >= failureThreshold && state != State.OPEN) {
                    state = State.OPEN;
                    openedAt = Instant.now();
                    SunDBLogger.warn("[Circuit:" + dbName + "] OPENED after " + count +
                            " consecutive failures. DB appears unreachable.");
                }
            } else {
                if (state == State.HALF_OPEN) {
                    SunDBLogger.info("[Circuit:" + dbName + "] Probe succeeded — circuit CLOSED.");
                }
                failures.set(0);
                state = State.CLOSED;
            }
        });
    }

    public State state() {
        return state;
    }

    public int failureCount() {
        return failures.get();
    }
}
