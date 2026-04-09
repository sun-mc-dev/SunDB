package me.sunmc.db.impl.sql;

import me.sunmc.db.api.DatabaseType;
import me.sunmc.db.config.PoolConfig;
import me.sunmc.db.config.type.SQLiteConfig;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SQLiteDatabase extends AbstractSqlDatabase {

    private final SQLiteConfig cfg;

    public SQLiteDatabase(String name, SQLiteConfig cfg) {
        super(name);
        this.cfg = cfg;
    }

    // SQLite should stay single-connection to avoid write contention
    @Contract(pure = true)
    @Override
    protected @NotNull String jdbcUrl() {
        return cfg.jdbcUrl();
    }

    @Contract(pure = true)
    @Override
    protected @NotNull String driverClass() {
        return "org.sqlite.JDBC";
    }

    @Contract(pure = true)
    @Override
    protected @Nullable String username() {
        return null;
    }

    @Contract(pure = true)
    @Override
    protected @Nullable String password() {
        return null;
    }

    @Contract(" -> new")
    @Override
    protected @NotNull PoolConfig pool() {
        return PoolConfig.light();
    }

    @Override
    public DatabaseType type() {
        return DatabaseType.SQLITE;
    }
}