package me.sunmc.db.config;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

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

    @Contract(" -> new")
    @Override
    public @NotNull @Unmodifiable List<MavenLibrary> requiredLibraries() {
        return List.of(
                new MavenLibrary("com.zaxxer", "HikariCP", "7.0.2"),
                new MavenLibrary("org.mariadb.jdbc", "mariadb-java-client", "3.5.7")
        );
    }
}