package me.sunmc.db.config;

import java.util.List;

/**
 * Sealed hierarchy — each backend has its own concrete config.
 * Pattern-matching in DatabaseManager dispatches with zero casting.
 * <p>
 * Each permit declares its own runtime dependencies via {@link #requiredLibraries()},
 * enabling lazy, on-demand library loading — only the drivers actually used get downloaded.
 */
public sealed interface DatabaseConfig
        permits MySQLConfig, MariaDBConfig, PostgreSQLConfig,
        SQLiteConfig, H2Config, MongoConfig, RedisConfig {

    /**
     * Maven artifacts required at runtime by this database backend.
     * Called by {@link me.sunmc.db.api.DatabaseManager} before building
     * the connection, so only the needed drivers are loaded.
     */
    List<MavenLibrary> requiredLibraries();
}