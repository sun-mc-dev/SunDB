package me.sunmc.db;

import me.sunmc.db.api.AsyncDatabase;
import me.sunmc.db.api.DatabaseManager;
import me.sunmc.db.config.DatabaseConfig;
import me.sunmc.db.platform.PlatformAdapter;
import org.jetbrains.annotations.NotNull;

/**
 * Primary entry point. Register databases and obtain them by name.
 *
 * <pre>{@code
 * SunDB.register("main", MySQLConfig.of("host", 3306, "db", "user", "pass"));
 * AsyncDatabase db = SunDB.get("main");
 * db.queryAsync("SELECT 1").thenAccept(r -> r.forEach(System.out::println));
 * }</pre>
 */
public final class SunDB {

    private static DatabaseManager manager;

    static void init(PlatformAdapter adapter) {
        manager = new DatabaseManager(adapter);
    }

    public static void register(String name, DatabaseConfig config) {
        ensureReady();
        manager.register(name, config);
    }

    public static @NotNull AsyncDatabase get(String name) {
        ensureReady();
        return manager.get(name);
    }

    /**
     * Get the default (first registered) database.
     * Convenience for plugins that only use a single database.
     */
    public static @NotNull AsyncDatabase getDatabase() {
        ensureReady();
        return manager.getDefault();
    }

    public static void unregister(String name) {
        ensureReady();
        manager.unregister(name);
    }

    static void shutdown() {
        if (manager != null) manager.shutdownAll();
    }

    public static @NotNull DatabaseManager getManager() {
        ensureReady();
        return manager;
    }

    private static void ensureReady() {
        if (manager == null) throw new IllegalStateException("SunDB not initialized.");
    }
}
