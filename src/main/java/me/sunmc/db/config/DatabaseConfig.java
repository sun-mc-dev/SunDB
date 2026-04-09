package me.sunmc.db.config;

import me.sunmc.db.config.type.*;

/**
 * Sealed hierarchy — each backend has its own concrete config.
 * Pattern-matching in DatabaseManager dispatches with zero casting.
 */
public sealed interface DatabaseConfig
        permits MySQLConfig, MariaDBConfig, PostgreSQLConfig,
        SQLiteConfig, H2Config, MongoConfig, RedisConfig {
}