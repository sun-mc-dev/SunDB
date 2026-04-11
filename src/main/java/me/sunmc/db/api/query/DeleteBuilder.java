package me.sunmc.db.api.query;

import me.sunmc.db.api.AsyncDatabase;
import me.sunmc.db.api.result.UpdateResult;
import me.sunmc.db.util.SqlSanitizer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Fluent DELETE query builder.
 *
 * <pre>{@code
 * db.deleteFrom("players")
 *     .where("uuid", "=", uuid)
 *     .execute();
 * }</pre>
 */
public final class DeleteBuilder {

    private final AsyncDatabase database;
    private final String table;
    private final WhereClause where = new WhereClause();

    public DeleteBuilder(@NotNull AsyncDatabase database, @NotNull String table) {
        this.database = database;
        this.table = SqlSanitizer.table(table);
    }

    public DeleteBuilder where(String column, String operator, Object value) {
        where.where(column, operator, value);
        return this;
    }

    public DeleteBuilder and(String column, String operator, Object value) {
        where.and(column, operator, value);
        return this;
    }

    public DeleteBuilder or(String column, String operator, Object value) {
        where.or(column, operator, value);
        return this;
    }

    public UpdateResult execute() {
        return executeAsync().join();
    }

    public CompletableFuture<UpdateResult> executeAsync() {
        if (!where.hasConditions()) {
            throw new IllegalStateException("DELETE without WHERE clause is dangerous. " +
                    "Use update(\"DELETE FROM " + table + "\") directly if you really mean it.");
        }
        return database.updateAsync(toSql(), where.params().toArray());
    }

    @Contract(pure = true)
    public @NotNull String toSql() {
        return "DELETE FROM " + table + where.toSql();
    }
}
