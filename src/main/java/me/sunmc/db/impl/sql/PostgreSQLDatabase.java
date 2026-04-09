package me.sunmc.db.impl.sql;

import me.sunmc.db.api.DatabaseType;
import me.sunmc.db.config.PoolConfig;
import me.sunmc.db.config.type.PostgreSQLConfig;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public final class PostgreSQLDatabase extends AbstractSqlDatabase {

    private final PostgreSQLConfig cfg;

    public PostgreSQLDatabase(String name, PostgreSQLConfig cfg) {
        super(name);
        this.cfg = cfg;
    }

    @Contract(pure = true)
    @Override
    protected @NotNull String jdbcUrl() {
        return cfg.jdbcUrl();
    }

    @Contract(pure = true)
    @Override
    protected @NotNull String driverClass() {
        return "org.postgresql.Driver";
    }

    @Override
    protected String username() {
        return cfg.username();
    }

    @Override
    protected String password() {
        return cfg.password();
    }

    @Override
    protected PoolConfig pool() {
        return cfg.pool();
    }

    @Override
    public DatabaseType type() {
        return DatabaseType.POSTGRESQL;
    }
}
