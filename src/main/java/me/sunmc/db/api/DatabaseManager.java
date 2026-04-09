package me.sunmc.db.api;

import me.sunmc.db.config.*;
import me.sunmc.db.impl.mango.MongoDatabase;
import me.sunmc.db.impl.redis.RedisDatabase;
import me.sunmc.db.impl.sql.*;
import me.sunmc.db.platform.PlatformAdapter;
import me.sunmc.db.util.SunDBLogger;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;

public final class DatabaseManager {

    private final ConcurrentHashMap<String, AsyncDatabase> registry = new ConcurrentHashMap<>();
    private final PlatformAdapter platform;

    public DatabaseManager(PlatformAdapter platform) {
        this.platform = platform;
    }

    public void register(String name, DatabaseConfig config) {
        if (registry.containsKey(name))
            throw new IllegalArgumentException("Database already registered: " + name);

        AsyncDatabase db = build(name, config);
        db.connectAsync().exceptionally(ex -> {
            SunDBLogger.warn("Failed to connect database '" + name + "': " + ex.getMessage());
            return null;
        });
        registry.put(name, db);
    }

    public @NotNull AsyncDatabase get(String name) {
        AsyncDatabase db = registry.get(name);
        if (db == null) throw new IllegalArgumentException("Unknown database: " + name);
        return db;
    }

    public void unregister(String name) {
        AsyncDatabase db = registry.remove(name);
        if (db != null) db.disconnectAsync();
    }

    public void shutdownAll() {
        registry.forEach((name, db) -> db.disconnectAsync().join());
        registry.clear();
    }

    private AsyncDatabase build(String name, @NotNull DatabaseConfig cfg) {
        return switch (cfg) {
            case MySQLConfig c -> new MySQLDatabase(name, c);
            case MariaDBConfig c -> new MariaDBDatabase(name, c);
            case PostgreSQLConfig c -> new PostgreSQLDatabase(name, c);
            case SQLiteConfig c -> new SQLiteDatabase(name, c);
            case H2Config c -> new H2Database(name, c);
            case MongoConfig c -> new MongoDatabase(name, c);
            case RedisConfig c -> new RedisDatabase(name, c);
        };
    }
}