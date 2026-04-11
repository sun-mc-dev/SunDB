package me.sunmc.db.api.query;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable WHERE clause builder shared across SELECT, UPDATE, and DELETE builders.
 * Supports AND/OR chaining with parameterized values.
 */
public final class WhereClause {

    private final StringBuilder sql = new StringBuilder();
    private final List<Object> params = new ArrayList<>();
    private boolean hasCondition = false;

    /**
     * Add a WHERE or AND condition.
     */
    public WhereClause where(String column, String operator, Object value) {
        if (hasCondition) {
            sql.append(" AND ");
        } else {
            sql.append(" WHERE ");
            hasCondition = true;
        }
        sql.append(column).append(' ').append(operator).append(" ?");
        params.add(value);
        return this;
    }

    /**
     * Add an AND condition (alias for where when chaining).
     */
    public WhereClause and(String column, String operator, Object value) {
        return where(column, operator, value);
    }

    /**
     * Add an OR condition.
     */
    public WhereClause or(String column, String operator, Object value) {
        if (hasCondition) {
            sql.append(" OR ");
        } else {
            sql.append(" WHERE ");
            hasCondition = true;
        }
        sql.append(column).append(' ').append(operator).append(" ?");
        params.add(value);
        return this;
    }

    /**
     * Add a raw WHERE clause (for complex conditions).
     */
    public WhereClause whereRaw(@NotNull String rawSql, Object... values) {
        if (hasCondition) {
            sql.append(" AND ");
        } else {
            sql.append(" WHERE ");
            hasCondition = true;
        }
        sql.append(rawSql);
        params.addAll(List.of(values));
        return this;
    }

    @Contract(pure = true)
    public @NotNull String toSql() {
        return sql.toString();
    }

    public List<Object> params() {
        return params;
    }

    public boolean hasConditions() {
        return hasCondition;
    }
}
