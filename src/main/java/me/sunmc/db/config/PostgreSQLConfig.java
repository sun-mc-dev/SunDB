package me.sunmc.db.config;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public record PostgreSQLConfig(
        String host, int port, String database,
        String username, String password,
        PoolConfig pool
) implements DatabaseConfig {

    @Contract("_, _, _, _, _ -> new")
    public static @NotNull PostgreSQLConfig of(String host, int port, String db, String user, String pass) {
        return new PostgreSQLConfig(host, port, db, user, pass, PoolConfig.defaults());
    }

    @Contract(pure = true)
    public @NotNull String jdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database
                + "?reWriteBatchedInserts=true";
    }

    @Contract(" -> new")
    @Override
    public @NotNull @Unmodifiable List<MavenLibrary> requiredLibraries() {
        return List.of(
                new MavenLibrary("com.zaxxer", "HikariCP", "7.0.2"),
                new MavenLibrary("org.postgresql", "postgresql", "42.7.10")
        );
    }
}