package me.sunmc.db.api.result;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;

/**
 * Single row result — works for SQL rows, MongoDB documents, and Redis entries.
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

    public Map<String, Object> raw() {
        return data;
    }

    @Contract(pure = true)
    @Override
    public @NotNull String toString() {
        return "QueryResult" + data;
    }
}