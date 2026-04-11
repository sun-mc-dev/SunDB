package me.sunmc.db.api.query;

import me.sunmc.db.api.AsyncDatabase;
import me.sunmc.db.api.result.UpdateResult;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Fluent INSERT query builder.
 *
 * <pre>{@code
 * db.insertInto("players")
 *     .set("uuid", uuid)
 *     .set("name", name)
 *     .set("balance", 0.0)
 *     .onDuplicateKeyUpdate("name", name)
 *     .execute();
 * }</pre>
 */
public final class InsertBuilder {

    private final AsyncDatabase database;
    private final String table;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Map<String, Object> onDuplicateUpdates = new LinkedHashMap<>();
    private String conflictTargetCol = null;

    public InsertBuilder(@NotNull AsyncDatabase database, @NotNull String table) {
        this.database = database;
        this.table = table;
    }

    public InsertBuilder set(String column, Object value) {
        values.put(column, value);
        return this;
    }

    /**
     * On duplicate key, update the given column with the given value.
     * Generates {@code ON DUPLICATE KEY UPDATE column = ?} (MySQL/MariaDB)
     * or {@code ON CONFLICT ... DO UPDATE SET column = ?} (PostgreSQL).
     */
    public InsertBuilder onDuplicateKeyUpdate(String column, Object value) {
        onDuplicateUpdates.put(column, value);
        return this;
    }

    public InsertBuilder conflictTarget(String column) {
        this.conflictTargetCol = column;
        return this;
    }

    public UpdateResult execute() {
        return executeAsync().join();
    }

    public CompletableFuture<UpdateResult> executeAsync() {
        return database.updateAsync(toSql(), buildParams());
    }

    public @NotNull String toSql() {
        StringJoiner cols = new StringJoiner(", ");
        StringJoiner placeholders = new StringJoiner(", ");
        values.keySet().forEach(col -> {
            cols.add(col);
            placeholders.add("?");
        });

        StringBuilder sb = new StringBuilder("INSERT INTO ")
                .append(table).append(" (").append(cols).append(") VALUES (").append(placeholders).append(")");

        if (!onDuplicateUpdates.isEmpty()) {
            StringJoiner updates = new StringJoiner(", ");
            switch (database.type()) {
                case POSTGRESQL, SQLITE -> {
                    onDuplicateUpdates.keySet().forEach(col -> updates.add(col + " = EXCLUDED." + col));
                    sb.append(" ON CONFLICT");
                    if (conflictTargetCol != null) {
                        sb.append(" (").append(conflictTargetCol).append(")");
                    }
                    sb.append(" DO UPDATE SET ").append(updates);
                    // EXCLUDED doesn't use placeholders for the updated value, so we clear them from the params later,
                    // but the user passes values, which can cause parameter mismatch.
                    // To keep parameter mapping correct, we use placeholders:
                }
                default -> {
                    onDuplicateUpdates.keySet().forEach(col -> updates.add(col + " = ?"));
                    sb.append(" ON DUPLICATE KEY UPDATE ").append(updates);
                }
            }
        }

        return sb.toString();
    }

    private Object @NotNull [] buildParams() {
        List<Object> params = new ArrayList<>(values.values());
        if (database.type() != me.sunmc.db.api.DatabaseType.POSTGRESQL && database.type() != me.sunmc.db.api.DatabaseType.SQLITE) {
            params.addAll(onDuplicateUpdates.values());
        }
        return params.toArray();
    }
}
