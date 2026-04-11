package me.sunmc.db.impl.sql;

import me.sunmc.db.api.DatabaseType;
import me.sunmc.db.config.MySQLConfig;
import me.sunmc.db.config.PoolConfig;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public final class MySQLDatabase extends AbstractSqlDatabase {

    private final MySQLConfig cfg;

    public MySQLDatabase(String name, MySQLConfig cfg) {
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
        return "com.mysql.cj.jdbc.Driver";
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
        return DatabaseType.MYSQL;
    }
}