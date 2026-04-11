package me.sunmc.db.impl.sql;

import me.sunmc.db.api.DatabaseType;
import me.sunmc.db.config.MariaDBConfig;
import me.sunmc.db.config.PoolConfig;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public final class MariaDBDatabase extends AbstractSqlDatabase {

    private final MariaDBConfig cfg;

    public MariaDBDatabase(String name, MariaDBConfig cfg) {
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
        return "org.mariadb.jdbc.Driver";
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
        return DatabaseType.MARIADB;
    }
}