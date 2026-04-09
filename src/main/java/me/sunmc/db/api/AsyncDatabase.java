package me.sunmc.db.api;

import me.sunmc.db.api.result.QueryResult;
import me.sunmc.db.api.result.UpdateResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Unified async database interface for all supported backends.
 * All operations are non-blocking and execute on virtual threads.
 */
public interface AsyncDatabase {

    String name();

    /**
     * Fire-and-forget connect. Safe to call multiple times.
     */
    CompletableFuture<Void> connectAsync();

    CompletableFuture<Void> disconnectAsync();

    /**
     * SQL: parameterized SELECT. NoSQL: find by filter map.
     * Redis: GET by key pattern.
     */
    CompletableFuture<List<QueryResult>> queryAsync(String statement, Object... params);

    /**
     * SQL: INSERT/UPDATE/DELETE. NoSQL: upsert/delete. Redis: SET/DEL.
     */
    CompletableFuture<UpdateResult> updateAsync(String statement, Object... params);

    /**
     * Batch execute — SQL only. Ignored gracefully for NoSQL.
     */
    CompletableFuture<int[]> batchAsync(String statement, List<Object[]> paramBatches);

    /**
     * SQL: run inside a transaction and return result.
     * NoSQL/Redis: execute as atomic operation where supported.
     */
    <T> CompletableFuture<T> transactionAsync(TransactionCallback<T> callback);

    boolean isConnected();

    DatabaseType type();
}
