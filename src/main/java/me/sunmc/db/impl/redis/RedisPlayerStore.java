package me.sunmc.db.impl.redis;

import me.sunmc.db.api.AsyncDatabase;
import me.sunmc.db.api.store.PlayerStore;
import me.sunmc.db.util.ValueParser;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis-backed {@link PlayerStore}. Each player's data is stored in a Redis hash
 * keyed as {@code sundb:store:<storeName>:<uuid>}, with field → value entries.
 */
public final class RedisPlayerStore implements PlayerStore {

    private final AsyncDatabase redis;
    private final String prefix;

    public RedisPlayerStore(@NotNull AsyncDatabase redis, @NotNull String storeName) {
        this.redis = redis;
        this.prefix = "sundb:store:" + storeName + ":";
    }

    @Override
    public void set(@NotNull UUID player, @NotNull String key, @NotNull Object value) {
        String hashKey = prefix + player;
        redis.update(hashKey, "HSET", key, value.toString());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(@NotNull UUID player, @NotNull String key, @NotNull Class<T> type) {
        // Query the hash and get the specific field
        var rows = redis.query(prefix + player, "hash");
        if (rows.isEmpty()) return Optional.empty();

        String raw = rows.getFirst().getString(key);
        if (raw == null) return Optional.empty();

        return Optional.of(ValueParser.parse(raw, type));
    }

    @Override
    public Map<String, String> getAll(@NotNull UUID player) {
        var rows = redis.query(prefix + player, "hash");
        if (rows.isEmpty()) return Map.of();

        Map<String, String> result = new HashMap<>();
        rows.getFirst().raw().forEach((k, v) -> result.put(k, v != null ? v.toString() : null));
        return result;
    }

    @Override
    public boolean has(@NotNull UUID player, @NotNull String key) {
        return get(player, key, String.class).isPresent();
    }

    @Override
    public void remove(@NotNull UUID player, @NotNull String key) {
        // Use DEL-like approach for individual hash fields
        // We re-get all, remove the field, and re-set (Redis HDEL not directly exposed)
        redis.update(prefix + player, "HDEL", key, "");
    }

    @Override
    public void delete(@NotNull UUID player) {
        redis.update(prefix + player, "DEL");
    }

}
