package me.sunmc.db.config.type;

import me.sunmc.db.config.DatabaseConfig;
import me.sunmc.db.config.PoolConfig;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public record MariaDBConfig(
        String host, int port, String database,
        String username, String password,
        PoolConfig pool
) implements DatabaseConfig {

    @Contract("_, _, _, _, _ -> new")
    public static @NotNull MariaDBConfig of(String host, int port, String db, String user, String pass) {
        return new MariaDBConfig(host, port, db, user, pass, PoolConfig.defaults());
    }

    @Contract(pure = true)
    public @NotNull String jdbcUrl() {
        return "jdbc:mariadb://" + host + ":" + port + "/" + database
                + "?useSSL=false&autoReconnect=true&characterEncoding=UTF-8"
                + "&rewriteBatchedStatements=true";
    }
}