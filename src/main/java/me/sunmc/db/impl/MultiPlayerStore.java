package me.sunmc.db.impl;

import me.sunmc.db.api.store.PlayerStore;
import me.sunmc.db.util.VirtualExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A write-through caching layer that wraps two PlayerStores.
 * <p>
 * <b>Write-Through:</b> {@link #set(UUID, String, Object)} writes synchronously
 * to the cache (e.g. Redis), and spawns a fast virtual thread to asynchronously
 * update the persistent store (e.g. MySQL) in the background.
 * <p>
 * <b>Read-Through:</b> {@link #get(UUID, String, Class)} checks the cache first.
 * If present, returns instantly. If absent, hits the persistent store, backfills
 * the cache, and returns.
 */
public final class MultiPlayerStore implements PlayerStore {

    private final PlayerStore persistent;
    private final PlayerStore cache;

    public MultiPlayerStore(@NotNull PlayerStore persistent, @NotNull PlayerStore cache) {
        this.persistent = persistent;
        this.cache = cache;
    }

    @Override
    public void set(@NotNull UUID player, @NotNull String key, @NotNull Object value) {
        // Write instantly to cache
        cache.set(player, key, value);

        // Save persistently in the background
        CompletableFuture.runAsync(() -> {
            persistent.set(player, key, value);
        }, VirtualExecutor.get());
    }

    @Override
    public <T> @NotNull Optional<T> get(@NotNull UUID player, @NotNull String key, @NotNull Class<T> type) {
        Optional<T> cached = cache.get(player, key, type);
        if (cached.isPresent()) {
            return cached;
        }

        Optional<T> persisted = persistent.get(player, key, type);
        persisted.ifPresent(val -> {
            // Backfill cache
            CompletableFuture.runAsync(() -> cache.set(player, key, val), VirtualExecutor.get());
        });
        return persisted;
    }

    @Override
    public @NotNull Map<String, String> getAll(@NotNull UUID player) {
        Map<String, String> cached = cache.getAll(player);
        if (!cached.isEmpty()) return cached;

        Map<String, String> persisted = persistent.getAll(player);
        if (!persisted.isEmpty()) {
            // Backfill cache
            CompletableFuture.runAsync(() -> {
                for (var entry : persisted.entrySet()) {
                    if (entry.getValue() != null) cache.set(player, entry.getKey(), entry.getValue());
                }
            }, VirtualExecutor.get());
        }
        return persisted;
    }

    @Override
    public boolean has(@NotNull UUID player, @NotNull String key) {
        return cache.has(player, key) || persistent.has(player, key);
    }

    @Override
    public void remove(@NotNull UUID player, @NotNull String key) {
        cache.remove(player, key);
        CompletableFuture.runAsync(() -> persistent.remove(player, key), VirtualExecutor.get());
    }

    @Override
    public void delete(@NotNull UUID player) {
        cache.delete(player);
        CompletableFuture.runAsync(() -> persistent.delete(player), VirtualExecutor.get());
    }
}
