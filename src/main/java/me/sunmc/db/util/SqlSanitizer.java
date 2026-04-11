package me.sunmc.db.util;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * Prevents SQL injection in fluent builders by validating identifiers
 * (table names, column names, index names) before they are concatenated into SQL.
 * <p>
 * Allows: letters, digits, underscores, dots (for schema-qualified names), and backticks/quotes.
 * Rejects everything else — no spaces, semicolons, parentheses, comments, etc.
 */
public final class SqlSanitizer {

    /**
     * Valid SQL identifier: alphanumeric, underscore, dot, optionally wrapped in backticks or double quotes.
     */
    private static final Pattern VALID_IDENTIFIER = Pattern.compile(
            "^[`\"]?[a-zA-Z_][a-zA-Z0-9_.]*[`\"]?$"
    );

    private SqlSanitizer() {
    }

    /**
     * Validate that the given name is a safe SQL identifier.
     *
     * @param name  the identifier to validate
     * @param label a human-readable label for error messages (e.g. "table name", "column name")
     * @return the validated name, unchanged
     * @throws IllegalArgumentException if the name contains invalid characters
     */
    public static @NotNull String validateIdentifier(@NotNull String name, @NotNull String label) {
        if (name.isBlank()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        if (!VALID_IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Invalid " + label + ": '" + name + "'. " +
                            "Only letters, digits, underscores, and dots are allowed."
            );
        }
        return name;
    }

    /**
     * Shorthand for validating a table name.
     */
    public static @NotNull String table(@NotNull String name) {
        return validateIdentifier(name, "table name");
    }

    /**
     * Shorthand for validating a column name.
     */
    public static @NotNull String column(@NotNull String name) {
        return validateIdentifier(name, "column name");
    }
}
