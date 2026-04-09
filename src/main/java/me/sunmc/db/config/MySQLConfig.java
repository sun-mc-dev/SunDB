package me.sunmc.db.config;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public record MySQLConfig(
        String host, int port, String database,
        String username, String password,
        PoolConfig pool
) implements DatabaseConfig {

    @Contract("_, _, _, _, _ -> new")
    public static @NotNull MySQLConfig of(String host, int port, String db, String user, String pass) {
        return new MySQLConfig(host, port, db, user, pass, PoolConfig.defaults());
    }

    @Contract(pure = true)
    public @NotNull String jdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&autoReconnect=true&characterEncoding=UTF-8"
                + "&serverTimezone=UTC&rewriteBatchedStatements=true";
    }
}