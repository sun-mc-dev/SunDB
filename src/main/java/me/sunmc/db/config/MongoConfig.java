package me.sunmc.db.config;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public record MongoConfig(
        String connectionString,
        String database
) implements DatabaseConfig {

    @Contract("_, _ -> new")
    public static @NotNull MongoConfig of(String connStr, String db) {
        return new MongoConfig(connStr, db);
    }

    @Contract("_ -> new")
    public static @NotNull MongoConfig local(String db) {
        return new MongoConfig("mongodb://localhost:27017", db);
    }
}