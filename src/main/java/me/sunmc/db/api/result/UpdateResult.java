package me.sunmc.db.api.result;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Result of a write/update operation.
 */
public record UpdateResult(long affectedRows, Object generatedKey) {

    @Contract("_ -> new")
    public static @NotNull UpdateResult of(long affected) {
        return new UpdateResult(affected, null);
    }

    @Contract("_, _ -> new")
    public static @NotNull UpdateResult of(long affected, Object key) {
        return new UpdateResult(affected, key);
    }

    public boolean succeeded() {
        return affectedRows > 0;
    }
}