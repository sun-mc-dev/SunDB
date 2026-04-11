package me.sunmc.db.api.resilience;

import me.sunmc.db.api.AsyncDatabase;
import me.sunmc.db.api.DatabaseMetrics;
import me.sunmc.db.api.DatabaseType;
import me.sunmc.db.api.TransactionCallback;
import me.sunmc.db.api.result.QueryResult;
import me.sunmc.db.api.result.UpdateResult;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * Decorator that adds circuit breaking and backpressure to any {@link AsyncDatabase}.
 * <p>
 * <b>Circuit breaking:</b> After {@code failureThreshold} consecutive errors,
 * all queries fail immediately without touching the DB or its connection pool.
 * After {@code circuitTimeout}, a single probe is allowed through. On success,
 * normal operation resumes.
 * <p>
 * <b>Backpressure:</b> At most {@code maxConcurrentOps} operations may be
 * in-flight simultaneously. If this limit is exceeded, new calls are rejected
 * instantly with a {@link BackpressureException} rather than piling up in a
 * queue and causing latency spikes.
 *
 * <pre>{@code
 * AsyncDatabase db = SunDB.get("main")
 *     .withResilience(ResilientConfig.forLargeNetwork());
 *
 * // Handle circuit open / backpressure in your plugin:
 * db.queryAsync("SELECT * FROM players WHERE uuid = ?", uuid)
 *   .exceptionally(ex -> {
 *       if (ex.getCause() instanceof CircuitOpenException) {
 *           player.sendMessage("§cDatabase temporarily unavailable, try again shortly.");
 *       }
 *       return List.of();
 *   });
 * }</pre>
 */
public final class ResilientDatabase implements AsyncDatabase {

    private final AsyncDatabase delegate;
    private final CircuitBreaker circuit;
    private final Semaphore semaphore;
    private final int maxConcurrentOps;

    public ResilientDatabase(@NotNull AsyncDatabase delegate,
                             @NotNull ResilientConfig config) {
        this.delegate = delegate;
        this.circuit = new CircuitBreaker(
                delegate.name(),
                config.failureThreshold(),
                config.circuitTimeout());
        this.semaphore = new Semaphore(config.maxConcurrentOps(), true);
        this.maxConcurrentOps = config.maxConcurrentOps();
    }

    private <T> CompletableFuture<T> guarded(Supplier<CompletableFuture<T>> op) {
        if (!semaphore.tryAcquire()) {
            return CompletableFuture.failedFuture(new BackpressureException(
                    "Database '" + name() + "' is overloaded (" +
                            maxConcurrentOps + " ops in-flight). Request rejected. " +
                            "Consider a retry with exponential backoff."));
        }

        return circuit.execute(op)
                .whenComplete((r, e) -> semaphore.release());
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public DatabaseType type() {
        return delegate.type();
    }

    @Override
    public boolean isConnected() {
        return delegate.isConnected();
    }

    @Override
    public CompletableFuture<Void> connectAsync() {
        return delegate.connectAsync();
    }

    @Override
    public CompletableFuture<Void> disconnectAsync() {
        return delegate.disconnectAsync();
    }

    @Override
    public CompletableFuture<List<QueryResult>> queryAsync(String sql, Object... params) {
        return guarded(() -> delegate.queryAsync(sql, params));
    }

    @Override
    public CompletableFuture<UpdateResult> updateAsync(String sql, Object... params) {
        return guarded(() -> delegate.updateAsync(sql, params));
    }

    @Override
    public CompletableFuture<int[]> batchAsync(String sql, List<Object[]> batches) {
        return guarded(() -> delegate.batchAsync(sql, batches));
    }

    @Override
    public <T> CompletableFuture<T> transactionAsync(TransactionCallback<T> callback) {
        return guarded(() -> delegate.transactionAsync(callback));
    }

    @Override
    public @NotNull DatabaseMetrics metrics() {
        return delegate.metrics();
    }

    /**
     * Current state of the circuit breaker.
     */
    public CircuitBreaker.State circuitState() {
        return circuit.state();
    }

    /**
     * Number of DB operations currently in-flight.
     */
    public int activeOperations() {
        return maxConcurrentOps - semaphore.availablePermits();
    }

    /**
     * The underlying unwrapped database.
     */
    public AsyncDatabase unwrap() {
        return delegate;
    }
}