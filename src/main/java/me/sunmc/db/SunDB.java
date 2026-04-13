package me.sunmc.db;

import me.sunmc.db.api.AsyncDatabase;
import me.sunmc.db.api.DatabaseManager;
import me.sunmc.db.api.orm.Repository;
import me.sunmc.db.config.ConfigLoader;
import me.sunmc.db.config.DatabaseConfig;
import me.sunmc.db.platform.PlatformAdapter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

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

    private static final List<SlowQueryInfo> slowQueryLog = Collections.synchronizedList(new LinkedList<>());
    private static DatabaseManager manager;
    private static Path lastDataFolder;
    /**
     * Global threshold for logging slow SQL interactions
     */
    private static volatile long slowQueryThresholdMs = 50;

    public static long getSlowQueryThresholdMs() {
        return slowQueryThresholdMs;
    }

    public static void setSlowQueryThresholdMs(long thresholdMs) {
        slowQueryThresholdMs = thresholdMs;
    }

    public static void trackSlowQuery(String sql, long ms) {
        synchronized (slowQueryLog) {
            slowQueryLog.add(new SlowQueryInfo(sql, ms, Instant.now()));
            while (slowQueryLog.size() > 100) {
                slowQueryLog.removeFirst();
            }
        }
    }

    @Contract(pure = true)
    public static @Unmodifiable List<SlowQueryInfo> getSlowQueryLog() {
        return List.copyOf(slowQueryLog);
    }

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

    static void init(PlatformAdapter adapter, Path dataFolder) {
        manager = new DatabaseManager(adapter);
        lastDataFolder = dataFolder;
    }

    public static void reload(Path dataFolder) {
        ensureReady();
        lastDataFolder = dataFolder;
        manager.reloadAll();
        Repository.clearCache();
        ConfigLoader.load(dataFolder);
    }

    /**
     * Convenience overload — reuses the last known data folder.
     */
    public static void reload() {
        if (lastDataFolder == null)
            throw new IllegalStateException("SunDB has no recorded data folder; call reload(Path) instead.");
        reload(lastDataFolder);
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
        Repository.clearCache();
    }

    public static @NotNull DatabaseManager getManager() {
        ensureReady();
        return manager;
    }

    private static void ensureReady() {
        if (manager == null) throw new IllegalStateException("SunDB not initialized.");
    }

    public record SlowQueryInfo(String sql, long ms, Instant timestamp) {
    }
}
