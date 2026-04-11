package me.sunmc.db.api.query;

import me.sunmc.db.api.AsyncDatabase;
import me.sunmc.db.api.result.UpdateResult;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Fluent UPDATE query builder.
 *
 * <pre>{@code
 * db.updateTable("players")
 *     .set("name", newName)
 *     .set("balance", newBalance)
 *     .where("uuid", "=", uuid)
 *     .execute();
 * }</pre>
 */
public final class UpdateQueryBuilder {

    private final AsyncDatabase database;
    private final String table;
    private final Map<String, Object> setValues = new LinkedHashMap<>();
    private final WhereClause where = new WhereClause();

    public UpdateQueryBuilder(@NotNull AsyncDatabase database, @NotNull String table) {
        this.database = database;
        this.table = table;
    }

    public UpdateQueryBuilder set(String column, Object value) {
        setValues.put(column, value);
        return this;
    }

    public UpdateQueryBuilder where(String column, String operator, Object value) {
        where.where(column, operator, value);
        return this;
    }

    public UpdateQueryBuilder and(String column, String operator, Object value) {
        where.and(column, operator, value);
        return this;
    }

    public UpdateQueryBuilder or(String column, String operator, Object value) {
        where.or(column, operator, value);
        return this;
    }

    public UpdateResult execute() {
        return executeAsync().join();
    }

    public CompletableFuture<UpdateResult> executeAsync() {
        return database.updateAsync(toSql(), buildParams());
    }

    public @NotNull String toSql() {
        StringJoiner sets = new StringJoiner(", ");
        setValues.keySet().forEach(col -> sets.add(col + " = ?"));

        return "UPDATE " + table + " SET " + sets + where.toSql();
    }

    private Object @NotNull [] buildParams() {
        List<Object> params = new ArrayList<>(setValues.values());
        params.addAll(where.params());
        return params.toArray();
    }
}
