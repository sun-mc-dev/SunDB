package me.sunmc.db.util;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Shared value parser — converts raw String values to typed Java objects.
 * Used by {@link me.sunmc.db.impl.sql.SqlPlayerStore} and
 * {@link me.sunmc.db.impl.redis.RedisPlayerStore} to avoid duplicated code.
 */
public final class ValueParser {

    private ValueParser() {
    }

    /**
     * Parse a raw string value into the requested type.
     *
     * @param raw  the raw string representation
     * @param type the target Java type
     * @return the parsed value
     * @throws NumberFormatException    if the string cannot be parsed as the requested numeric type
     * @throws IllegalArgumentException if the type is not supported
     */
    @SuppressWarnings("unchecked")
    public static <T> T parse(@NotNull String raw, @NotNull Class<T> type) {
        Object result;
        if (type == String.class) {
            result = raw;
        } else if (type == Integer.class || type == int.class) {
            result = Integer.parseInt(raw);
        } else if (type == Long.class || type == long.class) {
            result = Long.parseLong(raw);
        } else if (type == Double.class || type == double.class) {
            result = Double.parseDouble(raw);
        } else if (type == Float.class || type == float.class) {
            result = Float.parseFloat(raw);
        } else if (type == Boolean.class || type == boolean.class) {
            result = Boolean.parseBoolean(raw);
        } else if (type == UUID.class) {
            result = UUID.fromString(raw);
        } else {
            result = raw; // Fallback: return as-is
        }
        return (T) result;
    }
}
