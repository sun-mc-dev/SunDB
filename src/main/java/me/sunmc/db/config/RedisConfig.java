package me.sunmc.db.config;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public record RedisConfig(
        String host, int port,
        String password,
        int database,
        boolean ssl,
        int maxConnections
) implements DatabaseConfig {

    @Contract("_, _ -> new")
    public static @NotNull RedisConfig of(String host, int port) {
        return new RedisConfig(host, port, null, 0, false, 8);
    }

    @Contract("_, _, _ -> new")
    public static @NotNull RedisConfig of(String host, int port, String password) {
        return new RedisConfig(host, port, password, 0, false, 8);
    }

    @Contract(" -> new")
    public static @NotNull RedisConfig local() {
        return of("localhost", 6379);
    }
}