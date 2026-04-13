package me.sunmc.db.config;

import me.sunmc.db.SunDB;
import me.sunmc.db.api.DatabaseType;
import me.sunmc.db.util.SunDBLogger;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Loads the central {@code config.yml} to register shared remote databases.
 */
public final class ConfigLoader {

    private ConfigLoader() {
    }

    /**
     * Ensure the config exists in the data directory, load it, and register databases.
     *
     * @param dataFolder The plugin data folder (e.g. plugins/SunDB)
     */
    public static void load(@NotNull Path dataFolder) {
        Path configFile = dataFolder.resolve("config.yml");

        try {
            Files.createDirectories(dataFolder);

            if (!Files.exists(configFile)) {
                try (InputStream in = SunDB.class.getResourceAsStream("/config.yml")) {
                    if (in != null) {
                        Files.copy(in, configFile);
                        SunDBLogger.info("Created default config.yml.");
                    } else {
                        SunDBLogger.warn("Could not find default config.yml in jar.");
                        return;
                    }
                }
            }

            Yaml yaml = new Yaml();
            Map<String, Object> root;
            try (Reader reader = Files.newBufferedReader(configFile)) {
                root = yaml.load(reader);
            }

            if (root == null) {
                SunDBLogger.warn("Empty config.yml.");
                return;
            }

            if (root.containsKey("global")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> global = (Map<String, Object>) root.get("global");
                if (global != null && global.containsKey("slow-query-threshold-ms")) {
                    SunDB.setSlowQueryThresholdMs(getInt(global, "slow-query-threshold-ms", 50));
                }
            }

            if (!root.containsKey("databases")) {
                SunDBLogger.warn("No 'databases' section found in config.yml.");
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> databases = (Map<String, Map<String, Object>>) root.get("databases");

            if (databases == null) return;

            for (Map.Entry<String, Map<String, Object>> entry : databases.entrySet()) {
                String name = entry.getKey();
                Map<String, Object> settings = entry.getValue();

                try {
                    registerDatabase(name, settings);
                } catch (Exception e) {
                    SunDBLogger.error("Failed to load database '" + name + "' from config: " + e.getMessage());
                }
            }

            // Second pass: setup ReadWrite routing for any entry that defined 'readers'
            for (Map.Entry<String, Map<String, Object>> entry : databases.entrySet()) {
                String name = entry.getKey();
                Map<String, Object> settings = entry.getValue();

                if (settings.containsKey("readers")) {
                    Object val = settings.get("readers");
                    if (val instanceof List<?> list) {
                        List<String> readerNames = list.stream().map(Object::toString).toList();
                        SunDB.getManager().registerReadWrite(name, name, readerNames);
                        SunDBLogger.info("Configured Read-Write routing for '" + name + "' with replicas: " + readerNames);
                    }
                }
            }

        } catch (Exception e) {
            SunDBLogger.error("Failed to load config.yml.");
            e.printStackTrace();
        }
    }

    private static void registerDatabase(String name, Map<String, Object> settings) {
        String typeStr = getString(settings, "type", "");
        if (typeStr.isBlank()) {
            throw new IllegalArgumentException("Missing 'type'");
        }

        DatabaseType type;
        try {
            type = DatabaseType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown type: " + typeStr);
        }

        // SQLite/H2 should generally not be configured in the global config.yml per our hybrid architecture,
        // but if an admin really wants a global SQLite database, they can.
        if (type == DatabaseType.SQLITE || type == DatabaseType.H2) {
            SunDBLogger.warn("Database '" + name + "' is " + type + ". File-based databases are best registered programmatically by individual plugins to avoid file locking.");
            // We ignore it for now or we could support it by reading a "file" config key.
            return;
        }

        String host = getString(settings, "host", "127.0.0.1");
        int port = getInt(settings, "port", type == DatabaseType.MYSQL ? 3306 : (type == DatabaseType.REDIS ? 6379 : 27017));
        String database = getString(settings, "database", "minecraft");
        String username = getString(settings, "username", "root");
        String password = getString(settings, "password", "");

        PoolConfig poolParams = PoolConfig.defaults();
        if (settings.containsKey("pool") && settings.get("pool") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> poolData = (Map<String, Object>) settings.get("pool");
            int max = getInt(poolData, "maximum-pool-size", poolParams.maximumPoolSize());
            int min = getInt(poolData, "minimum-idle", poolParams.minimumIdle());
            long timeout = getInt(poolData, "connection-timeout", (int) poolParams.connectionTimeoutMs());
            poolParams = new PoolConfig(max, min, timeout, poolParams.idleTimeoutMs(), poolParams.maxLifetimeMs(), poolParams.keepAliveTimeMs());
        }

        DatabaseConfig config = switch (type) {
            case MYSQL -> new MySQLConfig(host, port, database, username, password, poolParams);
            case MARIADB -> new MariaDBConfig(host, port, database, username, password, poolParams);
            case POSTGRESQL -> new PostgreSQLConfig(host, port, database, username, password, poolParams);
            case REDIS -> new RedisConfig(host, port, password, 0, false, poolParams.maximumPoolSize());
            case MONGODB -> new MongoConfig("mongodb://" + host + ":" + port, database);
            default -> throw new UnsupportedOperationException("Type " + type + " not supported in config.yml.");
        };

        SunDB.register(name, config);
        SunDBLogger.info("Registered database profile: '" + name + "' (" + type + ")");
    }

    private static String getString(@NotNull Map<String, Object> map, String key, String def) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : def;
    }

    private static int getInt(@NotNull Map<String, Object> map, String key, int def) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }
}
