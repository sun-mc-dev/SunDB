package me.sunmc.db.api.store;

import me.sunmc.db.util.PriorityExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * UUID-keyed key-value store for player data.
 * Every sync method has a non-blocking async counterpart.
 * On Folia, always prefer the Async variants inside event handlers.
 */
public interface PlayerStore {

    void set(@NotNull UUID player, @NotNull String key, @NotNull Object value);

    /**
     * Get a value for a player, parsed to the given type.
     */
    <T> Optional<T> get(@NotNull UUID player, @NotNull String key, @NotNull Class<T> type);

    /**
     * Get all key-value pairs for a player as raw strings.
     */
    Map<String, String> getAll(@NotNull UUID player);

    /**
     * Check if a key exists for a player.
     */
    boolean has(@NotNull UUID player, @NotNull String key);

    /**
     * Remove a specific key for a player.
     */
    void remove(@NotNull UUID player, @NotNull String key);

    /**
     * Delete all data for a player.
     */
    void delete(@NotNull UUID player);

    default <T> T getOrDefault(@NotNull UUID player, @NotNull String key,
                               @NotNull Class<T> type, @Nullable T def) {
        return get(player, key, type).orElse(def);
    }

    /**
     * Write a value asynchronously. Returns a future that completes once
     * the value is durably stored (or cached, for MultiPlayerStore).
     * Safe to call from any thread including Folia region threads.
     */
    default CompletableFuture<Void> setAsync(@NotNull UUID player,
                                             @NotNull String key,
                                             @NotNull Object value) {
        return CompletableFuture.runAsync(() -> set(player, key, value),
                PriorityExecutor.get());
    }

    /**
     * Read a value asynchronously. Use {@code .thenAccept()} to schedule
     * a callback back onto the Folia region thread.
     *
     * <pre>{@code
     * store.getAsync(uuid, "balance", Double.class)
     *      .thenAccept(opt -> {
     *          double bal = opt.orElse(0.0);
     *          // schedule callback back onto region thread if needed
     *      });
     * }</pre>
     */
    default <T> CompletableFuture<Optional<T>> getAsync(@NotNull UUID player,
                                                        @NotNull String key,
                                                        @NotNull Class<T> type) {
        return CompletableFuture.supplyAsync(() -> get(player, key, type),
                PriorityExecutor.get());
    }

    /**
     * Read a value asynchronously, returning the provided default if absent.
     */
    default <T> CompletableFuture<T> getOrDefaultAsync(@NotNull UUID player,
                                                       @NotNull String key,
                                                       @NotNull Class<T> type,
                                                       @Nullable T def) {
        return CompletableFuture.supplyAsync(
                () -> get(player, key, type).orElse(def),
                PriorityExecutor.get());
    }

    /**
     * Read all key-value pairs for a player asynchronously.
     */
    default CompletableFuture<Map<String, String>> getAllAsync(@NotNull UUID player) {
        return CompletableFuture.supplyAsync(() -> getAll(player),
                PriorityExecutor.get());
    }

    /**
     * Check if a key exists asynchronously.
     */
    default CompletableFuture<Boolean> hasAsync(@NotNull UUID player,
                                                @NotNull String key) {
        return CompletableFuture.supplyAsync(() -> has(player, key),
                PriorityExecutor.get());
    }

    /**
     * Remove a single key asynchronously.
     */
    default CompletableFuture<Void> removeAsync(@NotNull UUID player,
                                                @NotNull String key) {
        return CompletableFuture.runAsync(() -> remove(player, key),
                PriorityExecutor.get());
    }

    /**
     * Delete all data for a player asynchronously.
     */
    default CompletableFuture<Void> deleteAsync(@NotNull UUID player) {
        return CompletableFuture.runAsync(() -> delete(player),
                PriorityExecutor.get());
    }
}