// ─── ReadWriteDatabase.java ──────────────────────────────────────────────────
package me.sunmc.db.api.readwrite;

import me.sunmc.db.api.AsyncDatabase;
import me.sunmc.db.api.DatabaseMetrics;
import me.sunmc.db.api.DatabaseType;
import me.sunmc.db.api.TransactionCallback;
import me.sunmc.db.api.result.QueryResult;
import me.sunmc.db.api.result.UpdateResult;
import me.sunmc.db.util.SunDBLogger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Routes SELECT queries across read replicas (round-robin with health fallback)
 * and funnels all writes, batches, and transactions to the primary writer.
 *
 * <pre>{@code
 * AsyncDatabase primary  = SunDB.get("main");
 * AsyncDatabase replica1 = SunDB.get("main_replica_1");
 * AsyncDatabase replica2 = SunDB.get("main_replica_2");
 *
 * AsyncDatabase db = primary.withReadReplicas(replica1, replica2);
 *
 * // Automatically hits replica1 or replica2
 * db.queryAsync("SELECT * FROM players WHERE uuid = ?", uuid);
 *
 * // Always hits primary
 * db.updateAsync("UPDATE economy SET bal = ? WHERE uuid = ?", 500, uuid);
 * }</pre>
 * <p>
 * If all replicas are offline, reads automatically fall back to the primary.
 */
public final class ReadWriteDatabase implements AsyncDatabase {

    private final AsyncDatabase writer;
    private final List<AsyncDatabase> readers;
    private final AtomicInteger rrCounter = new AtomicInteger(0);

    public ReadWriteDatabase(@NotNull AsyncDatabase writer,
                             @NotNull AsyncDatabase... readers) {
        this.writer = writer;
        this.readers = List.of(readers);
    }

    /**
     * Round-robin reader selection with automatic failover to the primary
     * if all replicas are currently disconnected.
     */
    private AsyncDatabase nextReader() {
        if (readers.isEmpty()) return writer;

        int start = Math.abs(rrCounter.getAndIncrement());
        for (int i = 0; i < readers.size(); i++) {
            AsyncDatabase candidate = readers.get((start + i) % readers.size());
            if (candidate.isConnected()) return candidate;
        }

        SunDBLogger.warn("[ReadWrite] All read replicas are offline — falling back to primary writer.");
        return writer;
    }

    @Override
    public @NotNull String name() {
        return writer.name() + "[rw-" + readers.size() + "r]";
    }

    @Override
    public DatabaseType type() {
        return writer.type();
    }

    @Override
    public boolean isConnected() {
        return writer.isConnected();
    }

    @Override
    public @NotNull CompletableFuture<Void> connectAsync() {
        List<CompletableFuture<Void>> all = new ArrayList<>();
        all.add(writer.connectAsync());
        readers.forEach(r -> all.add(r.connectAsync()));
        return CompletableFuture.allOf(all.toArray(new CompletableFuture[0]));
    }

    @Override
    public @NotNull CompletableFuture<Void> disconnectAsync() {
        List<CompletableFuture<Void>> all = new ArrayList<>();
        all.add(writer.disconnectAsync());
        readers.forEach(r -> all.add(r.disconnectAsync()));
        return CompletableFuture.allOf(all.toArray(new CompletableFuture[0]));
    }

    /**
     * SELECT → read replica (or primary if all replicas are down).
     */
    @Override
    public CompletableFuture<List<QueryResult>> queryAsync(String sql, Object... params) {
        return nextReader().queryAsync(sql, params);
    }

    /**
     * INSERT / UPDATE / DELETE → primary writer only.
     */
    @Override
    public CompletableFuture<UpdateResult> updateAsync(String sql, Object... params) {
        return writer.updateAsync(sql, params);
    }

    /**
     * Batch inserts → primary writer only.
     */
    @Override
    public CompletableFuture<int[]> batchAsync(String sql, List<Object[]> batches) {
        return writer.batchAsync(sql, batches);
    }

    /**
     * Transactions always run on the primary writer — never on replicas.
     */
    @Override
    public <T> CompletableFuture<T> transactionAsync(TransactionCallback<T> callback) {
        return writer.transactionAsync(callback);
    }

    /**
     * Aggregated metrics across writer + all replicas.
     * The /sundb status command will show combined pool health.
     */
    @Override
    public @NotNull DatabaseMetrics metrics() {
        int active = 0, idle = 0, total = 0, waiters = 0;

        for (AsyncDatabase node : allNodes()) {
            DatabaseMetrics m = node.metrics();
            active += m.activeConnections();
            idle += m.idleConnections();
            total += m.totalConnections();
            waiters += m.threadsAwaitingConnection();
        }
        return new DatabaseMetrics(active, idle, total, waiters);
    }

    /**
     * The primary write database.
     */
    public AsyncDatabase writer() {
        return writer;
    }

    /**
     * All registered read replicas.
     */
    public List<AsyncDatabase> readers() {
        return readers;
    }

    /**
     * How many replicas are currently connected.
     */
    public long healthyReplicaCount() {
        return readers.stream().filter(AsyncDatabase::isConnected).count();
    }

    private @NotNull List<AsyncDatabase> allNodes() {
        List<AsyncDatabase> all = new ArrayList<>();
        all.add(writer);
        all.addAll(readers);
        return all;
    }
}