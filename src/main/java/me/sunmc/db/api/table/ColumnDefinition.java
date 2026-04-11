package me.sunmc.db.api.table;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Metadata for a single column in a {@link TableBuilder} definition.
 */
public record ColumnDefinition(
        String name,
        ColumnType type,
        int length,
        boolean nullable,
        boolean autoIncrement,
        @Nullable Object defaultValue
) {

    /**
     * Render this column as a SQL fragment for the given dialect.
     */
    public @NotNull String toSql(String dialect) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(' ').append(type.toSql(dialect, length));

        if (!nullable) sb.append(" NOT NULL");
        if (autoIncrement) {
            switch (dialect.toLowerCase()) {
                case "sqlite" -> {
                } // SQLite uses INTEGER PRIMARY KEY for autoincrement
                case "postgresql" -> sb.append(" GENERATED ALWAYS AS IDENTITY");
                default -> sb.append(" AUTO_INCREMENT");
            }
        }
        if (defaultValue != null) {
            sb.append(" DEFAULT ");
            if (defaultValue instanceof String s) {
                sb.append("'").append(s).append("'");
            } else {
                sb.append(defaultValue);
            }
        }
        return sb.toString();
    }
}
