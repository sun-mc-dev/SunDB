package me.sunmc.db.api.result;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Single row result — works for SQL rows, MongoDB documents, and Redis entries.
 * Provides typed getters for common Minecraft plugin data types.
 */
public final class QueryResult {

    private final Map<String, Object> data;

    public QueryResult(Map<String, Object> data) {
        this.data = Map.copyOf(data);
    }


    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key) {
        return Optional.ofNullable((T) data.get(key));
    }

    public <T> T getOrThrow(String key) {
        return this.<T>get(key).orElseThrow(() ->
                new IllegalArgumentException("Key not found: " + key));
    }

    public <T> T getOrDefault(String key, T def) {
        return this.<T>get(key).orElse(def);
    }

    public @Nullable String getString(String key) {
        Object val = data.get(key);
        return val != null ? val.toString() : null;
    }

    public int getInt(String key) {
        return getNumber(key).intValue();
    }

    public int getInt(String key, int def) {
        Object val = data.get(key);
        return val instanceof Number n ? n.intValue() : def;
    }

    public long getLong(String key) {
        return getNumber(key).longValue();
    }

    public long getLong(String key, long def) {
        Object val = data.get(key);
        return val instanceof Number n ? n.longValue() : def;
    }

    public double getDouble(String key) {
        return getNumber(key).doubleValue();
    }

    public double getDouble(String key, double def) {
        Object val = data.get(key);
        return val instanceof Number n ? n.doubleValue() : def;
    }

    public float getFloat(String key) {
        return getNumber(key).floatValue();
    }

    public boolean getBoolean(String key) {
        Object val = data.get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof Number n) return n.intValue() != 0;
        if (val instanceof String s) return Boolean.parseBoolean(s);
        return false;
    }

    public boolean getBoolean(String key, boolean def) {
        Object val = data.get(key);
        if (val == null) return def;
        if (val instanceof Boolean b) return b;
        if (val instanceof Number n) return n.intValue() != 0;
        if (val instanceof String s) return Boolean.parseBoolean(s);
        return def;
    }

    /**
     * Parse a UUID from a stored string (VARCHAR(36)) or native UUID column.
     */
    public @Nullable UUID getUUID(String key) {
        Object val = data.get(key);
        if (val instanceof UUID u) return u;
        if (val instanceof String s) return UUID.fromString(s);
        return null;
    }

    /**
     * Get a timestamp as {@link Instant}. Handles SQL {@link Timestamp},
     * epoch millis (Long), and ISO-8601 strings.
     */
    public @Nullable Instant getTimestamp(String key) {
        Object val = data.get(key);
        if (val instanceof Instant i) return i;
        if (val instanceof Timestamp ts) return ts.toInstant();
        if (val instanceof java.util.Date d) return d.toInstant();
        if (val instanceof Number n) return Instant.ofEpochMilli(n.longValue());
        if (val instanceof String s) return Instant.parse(s);
        return null;
    }

    public boolean hasKey(String key) {
        return data.containsKey(key);
    }

    public int size() {
        return data.size();
    }

    public Map<String, Object> raw() {
        return data;
    }

    private @NotNull Number getNumber(String key) {
        Object val = data.get(key);
        if (val instanceof Number n) return n;
        if (val instanceof String s) return Double.parseDouble(s);
        throw new ClassCastException("Key '" + key + "' is not a number: " + val);
    }

    @Contract(pure = true)
    @Override
    public @NotNull String toString() {
        return "QueryResult" + data;
    }
}