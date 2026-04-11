package me.sunmc.db.api.cache;

import me.sunmc.db.api.AsyncDatabase;
import me.sunmc.db.api.DatabaseMetrics;
import me.sunmc.db.api.DatabaseType;
import me.sunmc.db.api.TransactionCallback;
import me.sunmc.db.api.result.QueryResult;
import me.sunmc.db.api.result.UpdateResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decorator that wraps any {@link AsyncDatabase} with an in-memory query cache.
 * <p>
 * Cached entries expire after the configured TTL. Write operations invalidate
 * only cache entries that reference the same table (extracted from the SQL statement).
 * Falls back to full invalidation if the table name cannot be parsed.
 *
 * <pre>{@code
 * CachedDatabase cached = new CachedDatabase(db, CacheConfig.defaults());
 *
 * // First call: hits DB
 * cached.query("SELECT * FROM players WHERE uuid = ?", uuid);
 *
 * // Second call within TTL: instant, from memory
 * cached.query("SELECT * FROM players WHERE uuid = ?", uuid);
 *
 * // Writes invalidate only "players" cache entries, not everything
 * cached.update("UPDATE players SET name = ? WHERE uuid = ?", name, uuid);
 * }</pre>
 */
public final class CachedDatabase implements AsyncDatabase {

    /**
     * Matches common SQL patterns to extract the table name:
     * SELECT ... FROM table, INSERT INTO table, UPDATE table, DELETE FROM table
     */
    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "(?:FROM|INTO|UPDATE|DELETE\\s+FROM)\\s+([\\w.`\"]+)",
            Pattern.CASE_INSENSITIVE
    );

    private final AsyncDatabase delegate;
    private final CacheConfig config;
    private final ConcurrentHashMap<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<CacheKey> insertionOrder = new ConcurrentLinkedDeque<>();

    public CachedDatabase(@NotNull AsyncDatabase delegate, @NotNull CacheConfig config) {
        this.delegate = delegate;
        this.config = config;
    }

    private static @Nullable String extractTable(@NotNull String sql) {
        Matcher matcher = TABLE_PATTERN.matcher(sql);
        if (matcher.find()) {
            return matcher.group(1).replace("`", "").replace("\"", "").toLowerCase();
        }
        return null;
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public CompletableFuture<Void> connectAsync() {
        return delegate.connectAsync();
    }

    @Override
    public CompletableFuture<Void> disconnectAsync() {
        cache.clear();
        insertionOrder.clear();
        return delegate.disconnectAsync();
    }

    @Override
    public CompletableFuture<List<QueryResult>> queryAsync(String statement, Object... params) {
        CacheKey key = new CacheKey(statement, params);
        CacheEntry entry = cache.get(key);

        if (entry != null && !entry.isExpired(config.expireAfterWrite().toMillis())) {
            return CompletableFuture.completedFuture(entry.value());
        }

        return delegate.queryAsync(statement, params).thenApply(result -> {
            putCache(key, result);
            return result;
        });
    }

    @Override
    public CompletableFuture<UpdateResult> updateAsync(String statement, Object... params) {
        return delegate.updateAsync(statement, params).thenApply(result -> {
            invalidateByStatement(statement);
            return result;
        });
    }

    @Override
    public CompletableFuture<int[]> batchAsync(String statement, List<Object[]> paramBatches) {
        return delegate.batchAsync(statement, paramBatches).thenApply(result -> {
            invalidateByStatement(statement);
            return result;
        });
    }

    @Override
    public <T> CompletableFuture<T> transactionAsync(TransactionCallback<T> callback) {
        return delegate.transactionAsync(callback).thenApply(result -> {
            // Transactions can touch multiple tables — full invalidation is safest
            invalidateAll();
            return result;
        });
    }

    @Override
    public boolean isConnected() {
        return delegate.isConnected();
    }

    @Override
    public @NotNull DatabaseMetrics metrics() {
        return delegate.metrics();
    }

    @Override
    public DatabaseType type() {
        return delegate.type();
    }

    /**
     * Manually invalidate all cached entries.
     */
    public void invalidateAll() {
        cache.clear();
        insertionOrder.clear();
    }

    /**
     * Invalidate cached entries for a specific table.
     */
    public void invalidateTable(@NotNull String table) {
        String normalized = table.replace("`", "").replace("\"", "").toLowerCase();
        cache.keySet().removeIf(key -> {
            String t = extractTable(key.statement());
            return t != null && t.equals(normalized);
        });
    }

    /**
     * Current number of cached entries.
     */
    public int cacheSize() {
        return cache.size();
    }

    /**
     * Get the underlying (unwrapped) database.
     */
    public AsyncDatabase unwrap() {
        return delegate;
    }

    private void invalidateByStatement(@NotNull String statement) {
        String table = extractTable(statement);
        if (table != null) {
            invalidateTable(table);
        } else {
            // Can't determine table — fall back to full invalidation
            invalidateAll();
        }
    }

    private void putCache(CacheKey key, List<QueryResult> value) {
        // Evict oldest entries if over max size
        while (cache.size() >= config.maxSize()) {
            CacheKey oldest = insertionOrder.pollFirst();
            if (oldest != null) cache.remove(oldest);
        }
        cache.put(key, new CacheEntry(value, System.currentTimeMillis()));
        insertionOrder.addLast(key);
    }

    private record CacheKey(String statement, Object[] params) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CacheKey that)) return false;
            return Objects.equals(statement, that.statement) && Arrays.equals(params, that.params);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hashCode(statement) + Arrays.hashCode(params);
        }
    }

    private record CacheEntry(List<QueryResult> value, long createdAt) {
        boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - createdAt > ttlMs;
        }
    }
}

