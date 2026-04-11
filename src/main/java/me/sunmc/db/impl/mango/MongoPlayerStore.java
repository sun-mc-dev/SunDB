package me.sunmc.db.impl.mango;

import me.sunmc.db.api.AsyncDatabase;
import me.sunmc.db.api.result.QueryResult;
import me.sunmc.db.api.store.PlayerStore;
import me.sunmc.db.util.ValueParser;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * MongoDB-backed {@link PlayerStore}. Each player's data is stored as a document
 * in a collection named {@code sundb_store_<storeName>}, with the UUID as the
 * document filter key and arbitrary key-value fields.
 * <p>
 * Document structure:
 * <pre>{@code
 * {
 *   "uuid": "550e8400-e29b-41d4-a716-446655440000",
 *   "balance": "100.0",
 *   "rank": "VIP"
 * }
 * }</pre>
 */
public final class MongoPlayerStore implements PlayerStore {

    private final AsyncDatabase mongo;
    private final String collection;

    public MongoPlayerStore(@NotNull AsyncDatabase mongo, @NotNull String storeName) {
        this.mongo = mongo;
        this.collection = "sundb_store_" + storeName;
    }

    @Override
    public void set(@NotNull UUID player, @NotNull String key, @NotNull Object value) {
        // Query for existing document
        List<QueryResult> existing = mongo.query(collection, "uuid", player.toString());

        if (existing.isEmpty()) {
            // Insert new document with uuid + the key-value pair
            mongo.update(collection, "uuid", player.toString(), key, value.toString());
        } else {
            // MongoDB upsert: delete and re-insert with merged data
            Map<String, Object> data = new HashMap<>(existing.getFirst().raw());
            data.put(key, value.toString());
            data.put("uuid", player.toString());

            mongo.update(collection + "|delete", "uuid", player.toString());

            Object[] params = new Object[data.size() * 2];
            int i = 0;
            for (var entry : data.entrySet()) {
                params[i++] = entry.getKey();
                params[i++] = entry.getValue();
            }
            mongo.update(collection, params);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(@NotNull UUID player, @NotNull String key, @NotNull Class<T> type) {
        List<QueryResult> rows = mongo.query(collection, "uuid", player.toString());
        if (rows.isEmpty()) return Optional.empty();

        String raw = rows.getFirst().getString(key);
        if (raw == null) return Optional.empty();

        return Optional.of(ValueParser.parse(raw, type));
    }

    @Override
    public Map<String, String> getAll(@NotNull UUID player) {
        List<QueryResult> rows = mongo.query(collection, "uuid", player.toString());
        if (rows.isEmpty()) return Map.of();

        Map<String, String> result = new HashMap<>();
        rows.getFirst().raw().forEach((k, v) -> {
            if (!"uuid".equals(k) && !"_id".equals(k)) {
                result.put(k, v != null ? v.toString() : null);
            }
        });
        return result;
    }

    @Override
    public boolean has(@NotNull UUID player, @NotNull String key) {
        return get(player, key, String.class).isPresent();
    }

    @Override
    public void remove(@NotNull UUID player, @NotNull String key) {
        // Get existing, remove the key, delete + re-insert
        List<QueryResult> rows = mongo.query(collection, "uuid", player.toString());
        if (rows.isEmpty()) return;

        Map<String, Object> data = new HashMap<>(rows.getFirst().raw());
        data.remove(key);
        data.remove("_id"); // MongoDB internal ID — don't carry over

        mongo.update(collection + "|delete", "uuid", player.toString());

        if (data.size() > 1) { // Still has fields beyond uuid
            Object[] params = new Object[data.size() * 2];
            int i = 0;
            for (var entry : data.entrySet()) {
                params[i++] = entry.getKey();
                params[i++] = entry.getValue();
            }
            mongo.update(collection, params);
        }
    }

    @Override
    public void delete(@NotNull UUID player) {
        mongo.update(collection + "|delete", "uuid", player.toString());
    }
}
