package me.sunmc.db.config;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

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
}