package me.sunmc.db.config;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

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

    @Contract(" -> new")
    @Override
    public @NotNull @Unmodifiable List<MavenLibrary> requiredLibraries() {
        return List.of(
                new MavenLibrary("org.mongodb", "mongodb-driver-sync", "5.7.0-beta1"),
                new MavenLibrary("org.mongodb", "mongodb-driver-core", "5.7.0-beta1"),
                new MavenLibrary("org.mongodb", "bson", "5.7.0-beta1")
        );
    }
}