package me.sunmc.db.impl;

import me.sunmc.db.api.store.PlayerStore;
import me.sunmc.db.util.PriorityExecutor;
import me.sunmc.db.util.SunDBLogger;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Write-through cache combining a fast cache (Redis) and persistent store (MySQL).
 * <p>
 * Async overrides are provided here to avoid the double-wrapping that would occur
 * if the default interface implementations delegated to the sync methods:
 * default setAsync() → runAsync(() -> set())  — set() itself spawns a thread
 * → two virtual threads for one operation.
 * <p>
 * Instead, each async override does the work directly on a single virtual thread.
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
        cache.set(player, key, value);
        CompletableFuture.runAsync(() -> persistent.set(player, key, value),
                PriorityExecutor.get());
    }

    @Override
    public <T> @NotNull Optional<T> get(@NotNull UUID player, @NotNull String key,
                                        @NotNull Class<T> type) {
        Optional<T> cached = cache.get(player, key, type);
        if (cached.isPresent()) return cached;

        Optional<T> persisted = persistent.get(player, key, type);
        persisted.ifPresent(val ->
                CompletableFuture.runAsync(() -> cache.set(player, key, val),
                        PriorityExecutor.get()));
        return persisted;
    }

    @Override
    public @NotNull Map<String, String> getAll(@NotNull UUID player) {
        Map<String, String> cached = cache.getAll(player);
        if (!cached.isEmpty()) return cached;

        Map<String, String> persisted = persistent.getAll(player);
        if (!persisted.isEmpty()) {
            CompletableFuture.runAsync(() ->
                    persisted.forEach((k, v) -> {
                        if (v != null) cache.set(player, k, v);
                    }), PriorityExecutor.get());
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
        CompletableFuture.runAsync(() -> persistent.remove(player, key),
                PriorityExecutor.get());
    }

    @Override
    public void delete(@NotNull UUID player) {
        cache.delete(player);
        CompletableFuture.runAsync(() -> persistent.delete(player),
                PriorityExecutor.get());
    }

    @Override
    public CompletableFuture<Void> setAsync(@NotNull UUID player,
                                            @NotNull String key,
                                            @NotNull Object value) {
        // Write-to-cache and write-to-persistent run concurrently
        CompletableFuture<Void> cacheWrite = CompletableFuture.runAsync(
                () -> cache.set(player, key, value), PriorityExecutor.get());
        CompletableFuture<Void> persistWrite = CompletableFuture.runAsync(
                () -> persistent.set(player, key, value), PriorityExecutor.get());
        // Return when cache write is done (persist continues in background)
        return cacheWrite.whenComplete((r, e) ->
                persistWrite.exceptionally(ex -> {
                    // Log but don't fail the caller — cache write succeeded
                    SunDBLogger.error("[SunDB] MultiStore background persist failed: "
                            + ex.getMessage());
                    return null;
                }));
    }

    @Override
    public <T> CompletableFuture<Optional<T>> getAsync(@NotNull UUID player,
                                                       @NotNull String key,
                                                       @NotNull Class<T> type) {
        return CompletableFuture.supplyAsync(
                        () -> cache.get(player, key, type), PriorityExecutor.get())
                .thenCompose(cached -> {
                    if (cached.isPresent()) return CompletableFuture.completedFuture(cached);
                    return CompletableFuture.supplyAsync(() -> {
                        Optional<T> persisted = persistent.get(player, key, type);
                        persisted.ifPresent(val ->
                                CompletableFuture.runAsync(
                                        () -> cache.set(player, key, val), PriorityExecutor.get()));
                        return persisted;
                    }, PriorityExecutor.get());
                });
    }

    @Override
    public CompletableFuture<Map<String, String>> getAllAsync(@NotNull UUID player) {
        return CompletableFuture.supplyAsync(
                        () -> cache.getAll(player), PriorityExecutor.get())
                .thenCompose(cached -> {
                    if (!cached.isEmpty()) return CompletableFuture.completedFuture(cached);
                    return CompletableFuture.supplyAsync(() -> {
                        Map<String, String> persisted = persistent.getAll(player);
                        if (!persisted.isEmpty()) {
                            CompletableFuture.runAsync(() ->
                                    persisted.forEach((k, v) -> {
                                        if (v != null) cache.set(player, k, v);
                                    }), PriorityExecutor.get());
                        }
                        return persisted;
                    }, PriorityExecutor.get());
                });
    }

    @Override
    public @NotNull CompletableFuture<Void> removeAsync(@NotNull UUID player, @NotNull String key) {
        CompletableFuture<Void> cacheRemove = CompletableFuture.runAsync(
                () -> cache.remove(player, key), PriorityExecutor.get());
        CompletableFuture.runAsync(() -> persistent.remove(player, key), PriorityExecutor.get());
        return cacheRemove;
    }

    @Override
    public @NotNull CompletableFuture<Void> deleteAsync(@NotNull UUID player) {
        CompletableFuture<Void> cacheDel = CompletableFuture.runAsync(
                () -> cache.delete(player), PriorityExecutor.get());
        CompletableFuture.runAsync(() -> persistent.delete(player), PriorityExecutor.get());
        return cacheDel;
    }
}