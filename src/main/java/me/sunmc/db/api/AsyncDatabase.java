package me.sunmc.db.api;

import me.sunmc.db.api.cache.CacheConfig;
import me.sunmc.db.api.cache.CachedDatabase;
import me.sunmc.db.api.migration.MigrationBuilder;
import me.sunmc.db.api.query.DeleteBuilder;
import me.sunmc.db.api.query.InsertBuilder;
import me.sunmc.db.api.query.SelectBuilder;
import me.sunmc.db.api.query.UpdateQueryBuilder;
import me.sunmc.db.api.readwrite.ReadWriteDatabase;
import me.sunmc.db.api.resilience.ResilientConfig;
import me.sunmc.db.api.resilience.ResilientDatabase;
import me.sunmc.db.api.result.QueryResult;
import me.sunmc.db.api.result.UpdateResult;
import me.sunmc.db.api.store.PlayerStore;
import me.sunmc.db.api.table.TableBuilder;
import me.sunmc.db.impl.MultiPlayerStore;
import me.sunmc.db.impl.mango.MongoPlayerStore;
import me.sunmc.db.impl.redis.RedisPlayerStore;
import me.sunmc.db.impl.sql.SqlPlayerStore;
import me.sunmc.db.util.PriorityExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Unified async database interface for all supported backends.
 * All operations are non-blocking and execute on virtual threads.
 * <p>
 * Sync convenience methods are provided as default implementations
 * that block via {@link CompletableFuture#join()}. Safe to use from
 * virtual threads — the carrier thread is never pinned.
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

    /**
     * Get a real-time snapshot of the connection pool metrics.
     */
    @NotNull DatabaseMetrics metrics();

    DatabaseType type();

    /**
     * Blocking query. Executes on a virtual thread and waits for the result.
     */
    default List<QueryResult> query(String statement, Object... params) {
        return queryAsync(statement, params).join();
    }

    /**
     * Blocking update. Returns when the write completes.
     */
    default UpdateResult update(String statement, Object... params) {
        return updateAsync(statement, params).join();
    }

    /**
     * Blocking batch execute.
     */
    default int[] batch(String statement, List<Object[]> paramBatches) {
        return batchAsync(statement, paramBatches).join();
    }

    /**
     * Blocking transaction with automatic commit/rollback.
     */
    default <T> T transaction(TransactionCallback<T> callback) {
        return transactionAsync(callback).join();
    }

    /**
     * Convenience: query and return the first result, or null if empty.
     */
    default QueryResult queryFirst(String statement, Object... params) {
        List<QueryResult> results = query(statement, params);
        return results.isEmpty() ? null : results.getFirst();
    }

    /**
     * Convenience: async query returning only the first row.
     */
    default CompletableFuture<QueryResult> queryFirstAsync(String statement, Object... params) {
        return queryAsync(statement, params)
                .thenApply(rows -> rows.isEmpty() ? null : rows.getFirst());
    }

    /**
     * Start building a table definition for this database.
     * <p>
     * <b>SQL only.</b> Throws {@link UnsupportedOperationException} on Redis and MongoDB.
     *
     * <pre>{@code
     * db.table("players")
     *     .column("uuid", ColumnType.VARCHAR, 36)
     *     .column("name", ColumnType.VARCHAR, 16)
     *     .primaryKey("uuid")
     *     .createIfNotExists();
     * }</pre>
     */
    default TableBuilder table(String tableName) {
        requireSql("table()");
        return new TableBuilder(this, tableName);
    }

    /**
     * Start building versioned schema migrations for a namespace.
     * <p>
     * <b>SQL only.</b> Throws {@link UnsupportedOperationException} on Redis and MongoDB.
     *
     * <pre>{@code
     * db.migrate("my_plugin")
     *     .version(1, "CREATE TABLE players ...")
     *     .version(2, "ALTER TABLE players ADD COLUMN ...")
     *     .execute();
     * }</pre>
     */
    default MigrationBuilder migrate(String namespace) {
        requireSql("migrate()");
        return new MigrationBuilder(this, namespace);
    }

    /**
     * Create a UUID-keyed player data store.
     * Automatically selects the right implementation based on database type:
     * <ul>
     *   <li>SQL backends → {@link SqlPlayerStore}</li>
     *   <li>Redis → {@link RedisPlayerStore}</li>
     *   <li>MongoDB → {@link MongoPlayerStore}</li>
     * </ul>
     *
     * <pre>{@code
     * PlayerStore store = db.playerStore("my_plugin_data");
     * store.set(uuid, "balance", 100.0);
     * double bal = store.get(uuid, "balance", Double.class).orElse(0.0);
     * }</pre>
     */
    default PlayerStore playerStore(String storeName) {
        return switch (type()) {
            case REDIS -> new RedisPlayerStore(this, storeName);
            case MONGODB -> new MongoPlayerStore(this, storeName);
            default -> new SqlPlayerStore(this, storeName);
        };
    }

    /**
     * Create a composite write-through PlayerStore.
     * Combines a fast cache (e.g. Redis) with permanent storage (e.g. MySQL).
     *
     * <pre>{@code
     * PlayerStore store = db.multiStore(redisDb, "player_data");
     * }</pre>
     */
    default PlayerStore multiStore(@NotNull AsyncDatabase cacheDb, String storeName) {
        return new MultiPlayerStore(this.playerStore(storeName), cacheDb.playerStore(storeName));
    }

    /**
     * Wrap this database with an in-memory query cache.
     * <pre>{@code
     * CachedDatabase cached = db.withCache(CacheConfig.builder()
     *     .maxSize(1000)
     *     .expireAfterWrite(Duration.ofMinutes(5))
     *     .build());
     * }</pre>
     */
    default CachedDatabase withCache(CacheConfig config) {
        return new CachedDatabase(this, config);
    }

    /**
     * Start building a SELECT query.
     * <p>
     * <b>SQL only.</b> Throws {@link UnsupportedOperationException} on Redis and MongoDB.
     *
     * <pre>{@code
     * db.select("players")
     *     .columns("uuid", "name")
     *     .where("balance", ">", 100)
     *     .limit(10)
     *     .execute();
     * }</pre>
     */
    default SelectBuilder select(String table) {
        requireSql("select()");
        return new SelectBuilder(this, table);
    }

    /**
     * Start building an INSERT query.
     * <p>
     * <b>SQL only.</b> Throws {@link UnsupportedOperationException} on Redis and MongoDB.
     */
    default InsertBuilder insertInto(String table) {
        requireSql("insertInto()");
        return new InsertBuilder(this, table);
    }

    /**
     * Start building an UPDATE query.
     * <p>
     * <b>SQL only.</b> Throws {@link UnsupportedOperationException} on Redis and MongoDB.
     */
    default UpdateQueryBuilder updateTable(String table) {
        requireSql("updateTable()");
        return new UpdateQueryBuilder(this, table);
    }

    /**
     * Start building a DELETE query.
     * <p>
     * <b>SQL only.</b> Throws {@link UnsupportedOperationException} on Redis and MongoDB.
     */
    default DeleteBuilder deleteFrom(String table) {
        requireSql("deleteFrom()");
        return new DeleteBuilder(this, table);
    }

    /**
     * Wrap this database as the primary writer with one or more read replicas.
     * SELECT queries are distributed round-robin across replicas.
     * All writes and transactions always go to this (primary) instance.
     *
     * <pre>{@code
     * AsyncDatabase db = SunDB.get("main")
     *     .withReadReplicas(SunDB.get("main_r1"), SunDB.get("main_r2"));
     * }</pre>
     */
    default ReadWriteDatabase withReadReplicas(AsyncDatabase... replicas) {
        return new ReadWriteDatabase(this, replicas);
    }

    /**
     * Wrap this database with circuit breaking and backpressure.
     * Strongly recommended for any network serving 5,000+ concurrent players.
     *
     * <pre>{@code
     * AsyncDatabase db = SunDB.get("main")
     *     .withResilience(ResilientConfig.forLargeNetwork());
     * }</pre>
     */
    default ResilientDatabase withResilience(ResilientConfig config) {
        return new ResilientDatabase(this, config);
    }

    /**
     * Run a SELECT query on the CRITICAL executor — guaranteed not to queue
     * behind background work. Use for player join/quit and punishment checks.
     *
     * <pre>{@code
     * db.queryCriticalAsync("SELECT * FROM bans WHERE uuid = ?", uuid)
     *   .thenAccept(rows -> { ... });
     * }</pre>
     */
    default CompletableFuture<List<QueryResult>> queryCriticalAsync(String sql, Object... params) {
        return CompletableFuture.supplyAsync(
                () -> query(sql, params),
                PriorityExecutor.get(PriorityExecutor.Priority.CRITICAL));
    }

    /**
     * Run an UPDATE on the CRITICAL executor.
     * Use for economy transfers and other latency-sensitive writes.
     */
    default CompletableFuture<UpdateResult> updateCriticalAsync(String sql, Object... params) {
        return CompletableFuture.supplyAsync(
                () -> update(sql, params),
                PriorityExecutor.get(PriorityExecutor.Priority.CRITICAL));
    }

    /**
     * Run a SELECT on the BACKGROUND executor — rate-limited to avoid starving
     * critical operations. Use for leaderboard rebuilds, stats aggregation, etc.
     *
     * <pre>{@code
     * // Safe to spam — bounded pool prevents it overwhelming critical work
     * db.queryBackgroundAsync("SELECT name, kills FROM stats ORDER BY kills DESC LIMIT 100")
     *   .thenAccept(rows -> { ... });
     * }</pre>
     */
    default CompletableFuture<List<QueryResult>> queryBackgroundAsync(String sql, Object... params) {
        return CompletableFuture.supplyAsync(
                () -> query(sql, params),
                PriorityExecutor.get(PriorityExecutor.Priority.BACKGROUND));
    }

    /**
     * Run a batch insert on the BACKGROUND executor.
     * Ideal for periodic data flushes that should not compete with active gameplay.
     */
    default CompletableFuture<int[]> batchBackgroundAsync(String sql, List<Object[]> batches) {
        return CompletableFuture.supplyAsync(
                () -> batch(sql, batches),
                PriorityExecutor.get(PriorityExecutor.Priority.BACKGROUND));
    }

    /**
     * Guard: throws if the current database is not SQL-based.
     */
    private void requireSql(String method) {
        if (type() == DatabaseType.REDIS || type() == DatabaseType.MONGODB) {
            throw new UnsupportedOperationException(
                    method + " is only supported on SQL databases (MySQL, MariaDB, PostgreSQL, SQLite, H2). " +
                            "Current database type: " + type()
            );
        }
    }
}
