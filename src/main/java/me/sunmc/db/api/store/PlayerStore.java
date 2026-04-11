package me.sunmc.db.api.store;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * UUID-keyed key-value store for player data.
 * Eliminates boilerplate for the most common Minecraft database pattern.
 *
 * <pre>{@code
 * PlayerStore store = db.playerStore("my_plugin_data");
 *
 * store.set(uuid, "balance", 100.0);
 * store.set(uuid, "rank", "VIP");
 *
 * double balance = store.get(uuid, "balance", Double.class).orElse(0.0);
 * Map<String, String> all = store.getAll(uuid);
 *
 * store.delete(uuid);
 * }</pre>
 */
public interface PlayerStore {

    /**
     * Set a value for a player. Creates or overwrites the entry.
     */
    void set(@NotNull UUID player, @NotNull String key, @NotNull Object value);

    /**
     * Get a value for a player, parsed to the given type.
     */
    <T> Optional<T> get(@NotNull UUID player, @NotNull String key, @NotNull Class<T> type);

    /**
     * Get a value with a default fallback.
     */
    default <T> T getOrDefault(@NotNull UUID player, @NotNull String key,
                               @NotNull Class<T> type, @Nullable T def) {
        return get(player, key, type).orElse(def);
    }

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
}
