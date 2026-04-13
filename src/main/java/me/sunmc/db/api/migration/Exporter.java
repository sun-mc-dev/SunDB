package me.sunmc.db.api.migration;

import me.sunmc.db.api.AsyncDatabase;
import me.sunmc.db.api.result.QueryResult;
import me.sunmc.db.util.SqlSanitizer;
import me.sunmc.db.util.SunDBLogger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class Exporter {
    private Exporter() {
    }

    /**
     * Cross-database migration logic. Copies schema and data from source to target.
     * Note: This is an alpha-level implementation that copies data rows.
     */
    @Contract("_, _ -> new")
    public static @NotNull CompletableFuture<Void> export(@NotNull AsyncDatabase source, @NotNull AsyncDatabase target) {
        return CompletableFuture.runAsync(() -> {
            try {
                SunDBLogger.info("[Exporter] Starting migration from " + source.name() + " to " + target.name());

                List<String> tables = getTableNames(source);
                for (String table : tables) {
                    if (table.equalsIgnoreCase("sundb_migrations")) continue;

                    SunDBLogger.info("[Exporter] Migrating table: " + table);
                    migrateTable(source, target, table);
                }

                SunDBLogger.info("[Exporter] Migration completed successfully.");
            } catch (Exception e) {
                SunDBLogger.error("[Exporter] Migration failed: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    private static List<String> getTableNames(@NotNull AsyncDatabase db) {
        String query = switch (db.type()) {
            case SQLITE -> "SELECT name FROM sqlite_master WHERE type='table'";
            default ->
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() OR table_schema = 'public'";
        };

        try {
            List<QueryResult> results = db.query(query);
            return results.stream()
                    .map(r -> r.hasKey("name") ? r.getString("name") : r.getString("table_name"))
                    .toList();
        } catch (Exception e) {
            SunDBLogger.warn("Could not fetch tables for " + db.name() + ": " + e.getMessage());
            return List.of();
        }
    }

    private static void migrateTable(@NotNull AsyncDatabase source, AsyncDatabase target, String table) {
        String sanitizedTable = SqlSanitizer.table(table);
        List<QueryResult> rows = source.query("SELECT * FROM " + sanitizedTable);
        if (rows.isEmpty()) return;

        // Simple row-by-row migration for alpha (could be batched in future)
        for (QueryResult row : rows) {
            Map<String, Object> data = row.raw();
            List<String> columns = new ArrayList<>(data.keySet());

            StringBuilder sql = new StringBuilder("INSERT INTO ").append(sanitizedTable).append(" (");
            sql.append(String.join(", ", columns)).append(") VALUES (");
            for (int i = 0; i < columns.size(); i++) {
                sql.append("?");
                if (i < columns.size() - 1) sql.append(", ");
            }
            sql.append(")");

            Object[] params = columns.stream().map(data::get).toArray();
            target.update(sql.toString(), params);
        }
    }
}
