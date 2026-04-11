package me.sunmc.db.impl.sql;

import me.sunmc.db.api.DatabaseType;
import me.sunmc.db.config.H2Config;
import me.sunmc.db.config.PoolConfig;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public final class H2Database extends AbstractSqlDatabase {

    private final H2Config cfg;

    public H2Database(String name, H2Config cfg) {
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
        return "org.h2.Driver";
    }

    @Contract(pure = true)
    @Override
    protected @NotNull String username() {
        return "sa";
    }

    @Contract(pure = true)
    @Override
    protected @NotNull String password() {
        return "";
    }

    @Contract(" -> new")
    @Override
    protected @NotNull PoolConfig pool() {
        return PoolConfig.light();
    }

    @Override
    public DatabaseType type() {
        return DatabaseType.H2;
    }
}