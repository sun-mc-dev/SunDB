package me.sunmc.db.api.query;

import me.sunmc.db.api.AsyncDatabase;
import me.sunmc.db.api.result.QueryResult;
import me.sunmc.db.util.SqlSanitizer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;

/**
 * Fluent SELECT query builder.
 *
 * <pre>{@code
 * List<QueryResult> results = db.select("players")
 *     .columns("uuid", "name", "balance")
 *     .where("balance", ">", 100)
 *     .and("last_login", ">", someInstant)
 *     .orderBy("balance", Order.DESC)
 *     .limit(10)
 *     .execute();
 * }</pre>
 */
public final class SelectBuilder {

    private final AsyncDatabase database;
    private final String table;
    private final List<String> columns = new ArrayList<>();
    private final WhereClause where = new WhereClause();
    private String orderByColumn;
    private Order orderDirection;
    private int limit = -1;
    private int offset = -1;

    public SelectBuilder(@NotNull AsyncDatabase database, @NotNull String table) {
        this.database = database;
        this.table = SqlSanitizer.table(table);
    }

    @Contract("_ -> this")
    public SelectBuilder columns(String @NotNull ... cols) {
        for (String col : cols) columns.add(SqlSanitizer.column(col));
        return this;
    }

    public SelectBuilder where(String column, String operator, Object value) {
        where.where(column, operator, value);
        return this;
    }

    public SelectBuilder and(String column, String operator, Object value) {
        where.and(column, operator, value);
        return this;
    }

    public SelectBuilder or(String column, String operator, Object value) {
        where.or(column, operator, value);
        return this;
    }

    public SelectBuilder orderBy(String column, Order direction) {
        this.orderByColumn = column;
        this.orderDirection = direction;
        return this;
    }

    public SelectBuilder limit(int limit) {
        this.limit = limit;
        return this;
    }

    public SelectBuilder offset(int offset) {
        this.offset = offset;
        return this;
    }

    /**
     * Build and execute the SELECT query synchronously.
     */
    public List<QueryResult> execute() {
        return executeAsync().join();
    }

    /**
     * Build and execute the SELECT query asynchronously.
     */
    public CompletableFuture<List<QueryResult>> executeAsync() {
        return database.queryAsync(toSql(), where.params().toArray());
    }

    /**
     * Execute and return only the first result.
     */
    public @Nullable QueryResult executeFirst() {
        List<QueryResult> results = execute();
        return results.isEmpty() ? null : results.getFirst();
    }

    public @NotNull String toSql() {
        StringBuilder sb = new StringBuilder("SELECT ");

        if (columns.isEmpty()) {
            sb.append("*");
        } else {
            StringJoiner joiner = new StringJoiner(", ");
            columns.forEach(joiner::add);
            sb.append(joiner);
        }

        sb.append(" FROM ").append(table);
        sb.append(where.toSql());

        if (orderByColumn != null) {
            sb.append(" ORDER BY ").append(orderByColumn)
                    .append(' ').append(orderDirection != null ? orderDirection.sql() : "ASC");
        }

        if (limit > 0) sb.append(" LIMIT ").append(limit);
        if (offset > 0) sb.append(" OFFSET ").append(offset);

        return sb.toString();
    }

    public enum Order {
        ASC, DESC;

        @Contract(pure = true)
        public @NotNull String sql() {
            return name();
        }
    }
}
