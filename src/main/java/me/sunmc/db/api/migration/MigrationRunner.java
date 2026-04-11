package me.sunmc.db.api.migration;

import me.sunmc.db.api.AsyncDatabase;
import me.sunmc.db.util.SunDBLogger;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.TreeMap;

/**
 * Executes versioned schema migrations against an SQL database.
 * Tracks which versions have been applied in the {@code sundb_migrations} table.
 * <p>
 * The migrations table has columns: {@code namespace}, {@code version}, {@code applied_at}.
 * Each (namespace, version) pair is unique — ensuring idempotent runs.
 */
public final class MigrationRunner {

    private MigrationRunner() {
    }

    /**
     * Run all pending migrations for the given namespace.
     */
    public static void run(@NotNull AsyncDatabase database,
                           @NotNull String namespace,
                           @NotNull Map<Integer, String> migrations) {

        database.update("""
                CREATE TABLE IF NOT EXISTS sundb_migrations (
                    namespace VARCHAR(128) NOT NULL,
                    version INT NOT NULL,
                    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (namespace, version)
                )
                """);

        int currentVersion = 0;
        var rows = database.query(
                "SELECT COALESCE(MAX(version), 0) AS max_ver FROM sundb_migrations WHERE namespace = ?",
                namespace
        );
        if (!rows.isEmpty()) {
            currentVersion = rows.getFirst().getInt("max_ver", 0);
        }

        TreeMap<Integer, String> sorted = new TreeMap<>(migrations);
        int applied = 0;

        for (var entry : sorted.entrySet()) {
            int ver = entry.getKey();
            if (ver <= currentVersion) continue;

            String sql = entry.getValue();
            SunDBLogger.info("[Migration] " + namespace + " v" + ver + ": " + truncate(sql));

            try {
                database.update(sql);
                database.update(
                        "INSERT INTO sundb_migrations (namespace, version) VALUES (?, ?)",
                        namespace, ver
                );
                applied++;
            } catch (Exception e) {
                SunDBLogger.error("[Migration] Failed at " + namespace + " v" + ver + ": " + e.getMessage());
                throw new RuntimeException("Migration failed at version " + ver, e);
            }
        }

        if (applied > 0) {
            SunDBLogger.info("[Migration] " + namespace + ": applied " + applied + " migration(s).");
        }
    }

    private static @NotNull String truncate(@NotNull String sql) {
        String trimmed = sql.strip().replaceAll("\\s+", " ");
        return trimmed.length() > 80 ? trimmed.substring(0, 77) + "..." : trimmed;
    }
}
