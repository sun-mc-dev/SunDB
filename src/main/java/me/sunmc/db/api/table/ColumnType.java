package me.sunmc.db.api.table;

/**
 * SQL column types with cross-dialect mapping.
 * Used by {@link TableBuilder} to generate portable DDL.
 */
public enum ColumnType {

    /**
     * Variable-length string. Requires a length parameter.
     * MySQL/MariaDB/PostgreSQL: VARCHAR(n), SQLite: TEXT, H2: VARCHAR(n)
     */
    VARCHAR,

    /**
     * Unlimited-length text.
     */
    TEXT,

    /**
     * 32-bit integer.
     */
    INT,

    /**
     * 64-bit integer.
     */
    BIGINT,

    /**
     * Single-precision float.
     */
    FLOAT,

    /**
     * Double-precision float.
     */
    DOUBLE,

    /**
     * Boolean (stored as TINYINT(1) on MySQL/MariaDB).
     */
    BOOLEAN,

    /**
     * Timestamp with timezone awareness.
     * MySQL: DATETIME, PostgreSQL: TIMESTAMP, SQLite: TEXT
     */
    TIMESTAMP,

    /**
     * Binary large object.
     */
    BLOB;

    /**
     * Render this type as a SQL type string for the given database dialect.
     */
    public String toSql(String dialect, int length) {
        boolean isSqlite = "sqlite".equalsIgnoreCase(dialect);

        return switch (this) {
            case VARCHAR -> isSqlite ? "TEXT" : "VARCHAR(" + (length > 0 ? length : 255) + ")";
            case TEXT -> "TEXT";
            case INT -> "INT";
            case BIGINT -> "BIGINT";
            case FLOAT -> isSqlite ? "REAL" : "FLOAT";
            case DOUBLE -> isSqlite ? "REAL" : "DOUBLE";
            case BOOLEAN -> switch (dialect.toLowerCase()) {
                case "mysql", "mariadb" -> "TINYINT(1)";
                case "sqlite" -> "INTEGER";
                default -> "BOOLEAN";
            };
            case TIMESTAMP -> switch (dialect.toLowerCase()) {
                case "mysql", "mariadb" -> "DATETIME";
                case "sqlite" -> "TEXT";
                default -> "TIMESTAMP";
            };
            case BLOB -> "BLOB";
        };
    }
}
