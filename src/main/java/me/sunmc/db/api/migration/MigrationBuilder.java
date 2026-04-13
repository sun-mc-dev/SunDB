package me.sunmc.db.api.migration;

import me.sunmc.db.api.AsyncDatabase;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fluent builder for versioned schema migrations.
 *
 * <pre>{@code
 * db.migrate("my_plugin")
 *     .version(1, "CREATE TABLE players (uuid VARCHAR(36) PRIMARY KEY, name VARCHAR(16))")
 *     .version(2, "ALTER TABLE players ADD COLUMN balance DOUBLE DEFAULT 0.0")
 *     .version(3, "CREATE INDEX idx_name ON players (name)")
 *     .execute();
 * }</pre>
 * <p>
 * SunDB tracks applied versions in a {@code sundb_migrations} table automatically.
 */
public final class MigrationBuilder {

    private final AsyncDatabase database;
    private final String namespace;
    private final Map<Integer, MigrationStep> migrations = new LinkedHashMap<>();

    public MigrationBuilder(@NotNull AsyncDatabase database, @NotNull String namespace) {
        this.database = database;
        this.namespace = namespace;
    }

    /**
     * Register an up-only migration at the given version.
     * Versions must be unique positive integers. They are executed in order.
     */
    public MigrationBuilder version(int version, String sql) {
        return version(version, sql, null);
    }

    /**
     * Register a reversible migration at the given version.
     *
     * @param version The version ID.
     * @param upSql   The SQL to execute when migrating UP.
     * @param downSql The SQL to execute when reverting DOWN.
     */
    public MigrationBuilder version(int version, String upSql, String downSql) {
        if (version < 1) throw new IllegalArgumentException("Version must be >= 1");
        if (migrations.containsKey(version))
            throw new IllegalArgumentException("Duplicate migration version: " + version);
        migrations.put(version, new MigrationStep(upSql, downSql));
        return this;
    }

    /**
     * Execute all pending migrations (versions not yet applied).
     */
    public void execute() {
        MigrationRunner.run(database, namespace, migrations);
    }

    public record MigrationStep(String upSql, String downSql) {
    }
}
