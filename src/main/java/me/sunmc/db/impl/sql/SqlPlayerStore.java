package me.sunmc.db.impl.sql;

import me.sunmc.db.api.AsyncDatabase;
import me.sunmc.db.api.store.PlayerStore;
import me.sunmc.db.util.ValueParser;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * SQL-backed {@link PlayerStore}. Stores data in a table with columns:
 * {@code uuid VARCHAR(36), data_key VARCHAR(128), data_value TEXT}.
 * <p>
 * Automatically creates the backing table on construction.
 */
public final class SqlPlayerStore implements PlayerStore {

    private final AsyncDatabase database;
    private final String tableName;

    public SqlPlayerStore(@NotNull AsyncDatabase database, @NotNull String storeName) {
        this.database = database;
        this.tableName = "sundb_store_" + storeName;
        createTable();
    }

    private void createTable() {
        database.update("""
                CREATE TABLE IF NOT EXISTS %s (
                    uuid VARCHAR(36) NOT NULL,
                    data_key VARCHAR(128) NOT NULL,
                    data_value TEXT,
                    PRIMARY KEY (uuid, data_key)
                )
                """.formatted(tableName));
    }

    @Override
    public void set(@NotNull UUID player, @NotNull String key, @NotNull Object value) {
        String uuid = player.toString();
        String val = value.toString();
        // Atomic upsert: single connection, single transaction — no race conditions
        database.transaction(ctx -> {
            ctx.update("DELETE FROM " + tableName + " WHERE uuid = ? AND data_key = ?", uuid, key);
            ctx.update("INSERT INTO " + tableName + " (uuid, data_key, data_value) VALUES (?, ?, ?)",
                    uuid, key, val);
            return null;
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(@NotNull UUID player, @NotNull String key, @NotNull Class<T> type) {
        var row = database.queryFirst(
                "SELECT data_value FROM " + tableName + " WHERE uuid = ? AND data_key = ?",
                player.toString(), key);

        if (row == null) return Optional.empty();

        String raw = row.getString("data_value");
        if (raw == null) return Optional.empty();

        return Optional.of(ValueParser.parse(raw, type));
    }

    @Override
    public @NotNull Map<String, String> getAll(@NotNull UUID player) {
        var rows = database.query(
                "SELECT data_key, data_value FROM " + tableName + " WHERE uuid = ?",
                player.toString());

        Map<String, String> result = new HashMap<>();
        for (var row : rows) {
            result.put(row.getString("data_key"), row.getString("data_value"));
        }
        return result;
    }

    @Override
    public boolean has(@NotNull UUID player, @NotNull String key) {
        var row = database.queryFirst(
                "SELECT 1 FROM " + tableName + " WHERE uuid = ? AND data_key = ?",
                player.toString(), key);
        return row != null;
    }

    @Override
    public void remove(@NotNull UUID player, @NotNull String key) {
        database.update("DELETE FROM " + tableName + " WHERE uuid = ? AND data_key = ?",
                player.toString(), key);
    }

    @Override
    public void delete(@NotNull UUID player) {
        database.update("DELETE FROM " + tableName + " WHERE uuid = ?", player.toString());
    }

}
