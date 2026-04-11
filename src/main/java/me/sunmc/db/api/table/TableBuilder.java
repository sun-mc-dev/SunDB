package me.sunmc.db.api.table;

import me.sunmc.db.api.AsyncDatabase;
import me.sunmc.db.util.SunDBLogger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Fluent builder for cross-dialect SQL table creation.
 *
 * <pre>{@code
 * db.table("players")                                // ← needs to be added to AsyncDatabase
 *     .column("uuid", ColumnType.VARCHAR, 36)
 *     .column("name", ColumnType.VARCHAR, 16)
 *     .column("balance", ColumnType.DOUBLE).defaultValue(0.0)
 *     .column("last_login", ColumnType.TIMESTAMP).nullable()
 *     .primaryKey("uuid")
 *     .index("idx_name", "name")
 *     .createIfNotExists();
 * }</pre>
 */
public final class TableBuilder {

    private final AsyncDatabase database;
    private final String tableName;
    private final List<ColumnEntry> columns = new ArrayList<>();
    private final List<String[]> indices = new ArrayList<>();
    private String primaryKey;

    public TableBuilder(@NotNull AsyncDatabase database, @NotNull String tableName) {
        this.database = database;
        this.tableName = tableName;
    }

    /**
     * Add a column with a type and default length.
     */
    public @NotNull ColumnEntry column(String name, ColumnType type) {
        return column(name, type, -1);
    }

    /**
     * Add a column with a type and explicit length (for VARCHAR etc).
     */
    public @NotNull ColumnEntry column(String name, ColumnType type, int length) {
        ColumnEntry entry = new ColumnEntry(this, name, type, length);
        columns.add(entry);
        return entry;
    }

    /**
     * Set the primary key column(s).
     */
    public TableBuilder primaryKey(String... columns) {
        this.primaryKey = String.join(", ", columns);
        return this;
    }

    /**
     * Add an index on one or more columns.
     */
    public TableBuilder index(String indexName, String... columns) {
        indices.add(new String[]{indexName, String.join(", ", columns)});
        return this;
    }

    /**
     * Execute CREATE TABLE IF NOT EXISTS using the database's dialect.
     */
    public void createIfNotExists() {
        String dialect = resolveDialect();
        String sql = buildCreateSql(dialect);
        database.update(sql);

        for (String[] idx : indices) {
            String idxSql = "CREATE INDEX IF NOT EXISTS " + idx[0] + " ON " + tableName + " (" + idx[1] + ")";
            try {
                database.update(idxSql);
            } catch (Exception e) {
                SunDBLogger.warn("Index creation skipped (may already exist): " + idx[0]);
            }
        }
    }

    private @NotNull String buildCreateSql(String dialect) {
        StringJoiner joiner = new StringJoiner(", ");
        for (ColumnEntry entry : columns) {
            joiner.add(entry.build().toSql(dialect));
        }
        if (primaryKey != null) {
            joiner.add("PRIMARY KEY (" + primaryKey + ")");
        }
        return "CREATE TABLE IF NOT EXISTS " + tableName + " (" + joiner + ")";
    }

    private @NotNull String resolveDialect() {
        return switch (database.type()) {
            case MYSQL -> "mysql";
            case MARIADB -> "mariadb";
            case POSTGRESQL -> "postgresql";
            case SQLITE -> "sqlite";
            case H2 -> "h2";
            default -> "mysql";
        };
    }

    /**
     * Fluent column entry — allows chaining `.nullable()`, `.defaultValue()`, etc.
     * Call another `.column()` or `.primaryKey()` to continue building the table.
     */
    public static final class ColumnEntry {

        private final TableBuilder parent;
        private final String name;
        private final ColumnType type;
        private final int length;
        private boolean nullable = false;
        private boolean autoIncrement = false;
        private Object defaultValue = null;

        ColumnEntry(TableBuilder parent, String name, ColumnType type, int length) {
            this.parent = parent;
            this.name = name;
            this.type = type;
            this.length = length;
        }

        public ColumnEntry nullable() {
            this.nullable = true;
            return this;
        }

        public ColumnEntry notNull() {
            this.nullable = false;
            return this;
        }

        public ColumnEntry autoIncrement() {
            this.autoIncrement = true;
            return this;
        }

        public ColumnEntry defaultValue(Object value) {
            this.defaultValue = value;
            return this;
        }

        /**
         * Continue adding columns.
         */
        public @NotNull ColumnEntry column(String name, ColumnType type) {
            return parent.column(name, type);
        }

        /**
         * Continue adding columns with length.
         */
        public @NotNull ColumnEntry column(String name, ColumnType type, int length) {
            return parent.column(name, type, length);
        }

        /**
         * Set primary key.
         */
        public TableBuilder primaryKey(String... columns) {
            return parent.primaryKey(columns);
        }

        /**
         * Add an index.
         */
        public TableBuilder index(String indexName, String... columns) {
            return parent.index(indexName, columns);
        }

        /**
         * Execute the table creation.
         */
        public void createIfNotExists() {
            parent.createIfNotExists();
        }

        @Contract(" -> new")
        @NotNull ColumnDefinition build() {
            return new ColumnDefinition(name, type, length, nullable, autoIncrement, defaultValue);
        }
    }
}
