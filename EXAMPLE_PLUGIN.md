# SunDB Real-World Examples

This document provides complete, copy-pasteable examples of how to build different types of plugins using SunDB's various features.

## Table of Contents
1. [Example 1: The ORM Statistics Plugin](#example-1-the-orm-statistics-plugin)
2. [Example 2: The Multi-Tier Player Settings Plugin](#example-2-the-multi-tier-player-settings-plugin)
3. [Example 3: Cross-Server Chat (Redis Pub/Sub)](#example-3-cross-server-chat-redis-pubsub)
4. [Example 4: High-Performance Leaderboards (Caching)](#example-4-high-performance-leaderboards-caching)
5. [Example 5: Isolated Local DB (SQLite)](#example-5-isolated-local-db-sqlite)
6. [Example 6: Secure Economy Transfers (Transactions)](#example-6-secure-economy-transfers-transactions)
7. [Example 7: Complex Custom Queries (Fluent Builders)](#example-7-complex-custom-queries-fluent-builders)
8. [Example 8: Production Upgrades (Migrations)](#example-8-production-upgrades-migrations)

---

## Example 1: The ORM Statistics Plugin
**Use Case:** Tracking complex structured objects (like User PROFILES, Guilds, or Homes).

### 1. The Entity Class

```java
import me.sunmc.db.api.orm.Column;
import me.sunmc.db.api.orm.Id;
import me.sunmc.db.api.orm.Table;
import java.util.UUID;

@Table("player_stats")
public class PlayerStats {

    @Id @Column("uuid")
    public UUID uuid;

    @Column("name")
    public String name;

    @Column("kills")
    public int kills;

    @Column("deaths")
    public int deaths;

    public PlayerStats() {}
    public PlayerStats(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }
}
```

### 2. The Plugin

```java
import me.sunmc.db.SunDB;
import me.sunmc.db.api.AsyncDatabase;
import me.sunmc.db.api.orm.Repository;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import me.sunmc.db.api.table.ColumnType;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public class StatsPlugin extends JavaPlugin implements Listener {

    private Repository<PlayerStats> repository;
    private final ConcurrentHashMap<UUID, PlayerStats> activeStats = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        // Grab global DB
        AsyncDatabase db = SunDB.getManager().get("main");

        // Ensure schema exists
        db.table("player_stats")
            .column("uuid", ColumnType.VARCHAR, 36).primaryKey()
            .column("name", ColumnType.VARCHAR, 16)
            .column("kills", ColumnType.INT, 0)
            .column("deaths", ColumnType.INT, 0)
            .createIfNotExists();

        this.repository = new Repository<>(db, PlayerStats.class);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        for (PlayerStats stats : activeStats.values()) repository.save(stats); 
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Loads asynchronously, won't freeze login
        repository.findByIdAsync(event.getPlayer().getUniqueId()).thenAccept(optionalStats -> {
            PlayerStats stats = optionalStats.orElseGet(() -> 
                new PlayerStats(event.getPlayer().getUniqueId(), event.getPlayer().getName())
            );
            activeStats.put(event.getPlayer().getUniqueId(), stats);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PlayerStats stats = activeStats.remove(event.getPlayer().getUniqueId());
        if (stats != null) repository.saveAsync(stats);
    }
}
```

---

## Example 2: The Multi-Tier Player Settings Plugin
**Use Case:** You want instantaneous read/write speeds using Redis, but you also want everything permanently backed up to MySQL.

```java
import me.sunmc.db.SunDB;
import me.sunmc.db.api.AsyncDatabase;
import me.sunmc.db.api.store.PlayerStore;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class SettingsPlugin extends JavaPlugin {

    private PlayerStore settings;

    @Override
    public void onEnable() {
        AsyncDatabase mysqlDb = SunDB.getManager().get("main");
        AsyncDatabase redisDb = SunDB.getManager().get("cache");

        // Wraps the native databases into a synchronized Write-Through Cache
        this.settings = mysqlDb.multiStore(redisDb, "player_settings");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (command.getName().equalsIgnoreCase("toggleflight")) {
            // Because we use multiStore, this checks Redis first.
            // If the key is there, it returns instantly.
            boolean canFly = settings.get(player.getUniqueId(), "flight", Boolean.class).orElse(false);

            // Writes instantly to Redis, and spawns a thread to backup to MySQL
            settings.set(player.getUniqueId(), "flight", !canFly);

            player.sendMessage("Flight toggled to: " + !canFly);
        }
        return true;
    }
}
```

---

## Example 3: Cross-Server Chat (Redis Pub/Sub)
**Use Case:** A specific message needs to instantly broadcast across every lobby and gamemode server connected to your network.

```java
import me.sunmc.db.SunDB;
import me.sunmc.db.api.AsyncDatabase;
import me.sunmc.db.api.DatabaseType;
import me.sunmc.db.impl.redis.RedisDatabase;
import org.bukkit.plugin.java.JavaPlugin;

public class AnnouncerPlugin extends JavaPlugin {

    private RedisDatabase redis;

    @Override
    public void onEnable() {
        AsyncDatabase db = SunDB.getManager().get("cache");
        
        if (db.type() != DatabaseType.REDIS) {
            getLogger().severe("The 'cache' database must be Redis for this plugin!");
            return;
        }
        
        this.redis = (RedisDatabase) db;

        // Subscribe to a network-wide channel
        redis.subscribe("network_announcements", (channel, message) -> {
            // Warning: PubSub runs on an asynchronous network thread!
            // We must push it back to the Spigot main thread to broadcast safely.
            getServer().getScheduler().runTask(this, () -> {
                getServer().broadcastMessage("§c[ALERT] §f" + message);
            });
        });
    }

    public void announceToNetwork(String message) {
        // Instantly sends to Velocity, Lobbies, and all other connected instances
        redis.publish("network_announcements", message);
    }
}
```

---

## Example 4: High-Performance Leaderboards (Caching)
**Use Case:** A command that runs an extremely heavy SQL query (like sorting 100,000 rows by balance), wrapped in a caching decorator to prevent players from crashing the server by spamming it.

```java
import me.sunmc.db.SunDB;
import me.sunmc.db.api.cache.CacheConfig;
import me.sunmc.db.api.cache.CachedDatabase;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;

public class EcoLeaderboardPlugin extends JavaPlugin {

    private CachedDatabase cachedDb;

    @Override
    public void onEnable() {
        // 1. Get raw database
        var rawDb = SunDB.getManager().get("main");

        // 2. Wrap it in a cache. We keep max 100 items for 30 seconds.
        CacheConfig config = CacheConfig.builder()
                .maxSize(100)
                .expireAfterWrite(Duration.ofSeconds(30))
                .build();
                
        this.cachedDb = rawDb.withCache(config);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage("§eFetching the top 10 richest players...");

        // Even if 100 players run this at the exact same moment, 
        // the SQL query only executes ONCE every 30 seconds.
        // The other 99 players get the result instantly from Caffeine RAM.
        cachedDb.queryAsync("SELECT name, bal FROM economy ORDER BY bal DESC LIMIT 10")
            .thenAccept(results -> {
                for (int i = 0; i < results.size(); i++) {
                    String name = results.get(i).getString("name");
                    int bal = results.get(i).getInt("bal", 0);
                    sender.sendMessage("§6#" + (i+1) + " §f" + name + " - $" + bal);
                }
            });

        return true;
    }
}
```

---

## Example 5: Isolated Local DB (SQLite)
**Use Case:** A small utility plugin that needs to save data, but shouldn't clog up the global Network MySQL database. You want it completely self-contained in a generic file.

```java
import me.sunmc.db.SunDB;
import me.sunmc.db.api.table.ColumnType;
import me.sunmc.db.api.AsyncDatabase;
import me.sunmc.db.config.SQLiteConfig;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;

public class PunishmentPlugin extends JavaPlugin {

    private AsyncDatabase db;

    @Override
    public void onEnable() {
        // Define our isolated SQLite file directly in plugins/PunishmentPlugin/bans.db
        Path file = getDataFolder().toPath().resolve("bans.db");
        SQLiteConfig config = SQLiteConfig.of(file.toString());

        // Register it dynamically into SunDB!
        SunDB.getManager().register("punishments", config);
        
        // Grab it
        this.db = SunDB.getManager().get("punishments");

        // Standard setup
        db.table("bans")
            .column("uuid", ColumnType.VARCHAR, 36).primaryKey()
            .column("reason", ColumnType.VARCHAR, 255)
            .createIfNotExists();
    }

    @Override
    public void onDisable() {
        // It's good practice to unregister isolated databases when your plugin reloading stops
        SunDB.getManager().unregister("punishments");
    }
}
```

---

## Example 6: Secure Economy Transfers (Transactions)
**Use Case:** You are moving money between two players. If the database crashes or an error happens after withdrawing from Player A, but before crediting Player B, the money vanishes. Transactions ensure either *everything* succeeds, or *nothing* happens.

```java
import me.sunmc.db.SunDB;
import me.sunmc.db.api.AsyncDatabase;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class EconomyTransferPlugin extends JavaPlugin {

    private AsyncDatabase db;

    @Override
    public void onEnable() {
        this.db = SunDB.getManager().get("main");
    }

    public void transferMoney(CommandSender sender, String playerA, String playerB, int amount) {
        // transactionAsync takes a custom callback block.
        // It injects a 'TransactionContext' which holds the connection state.
        db.transactionAsync(ctx -> {
            
            // 1. Withdraw
            ctx.update("UPDATE economy SET bal = bal - ? WHERE name = ?", amount, playerA);
            
            // 2. Credit
            int creditedRows = ctx.update("UPDATE economy SET bal = bal + ? WHERE name = ?", amount, playerB).rowsAffected();
            
            // 3. Logic Check: What if playerB doesn't exist?
            // If we throw an exception or return a failure, the transaction aborts
            // and Player A's withdrawn money is instantly returned.
            if (creditedRows == 0) {
                throw new IllegalStateException("Target player does not exist!");
            }
            
            return true; // Commit!
            
        }).whenComplete((success, err) -> {
            if (err != null) {
                sender.sendMessage("§cTransfer failed and was rolled back safely: " + err.getMessage());
            } else {
                sender.sendMessage("§a$" + amount + " logically transferred!");
            }
        });
    }
}
```

---

## Example 7: Complex Custom Queries (Fluent Builders)
**Use Case:** ORM is great for mapping whole objects, but sometimes you just need to query specific rows or join data dynamically using code.

```java
import me.sunmc.db.SunDB;
import me.sunmc.db.api.AsyncDatabase;
import me.sunmc.db.api.query.SelectBuilder;
import me.sunmc.db.api.result.QueryResult;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class SecurityPlugin extends JavaPlugin {

    public void auditBans(UUID target) {
        AsyncDatabase db = SunDB.getManager().get("main");
        Logger log = getLogger();

        // Build a type-safe SELECT query
        db.select("punishments")
          .columns("reason", "banned_by", "expires_at")
          .where("uuid", "=", target.toString())
          .and("active", "=", true)
          .orderBy("expires_at", SelectBuilder.Order.DESC)
          .executeAsync()
          .thenAccept(results -> {
              log.info("Found " + results.size() + " active punishments.");
              
              // QueryResult gracefully handles raw Types!
              for (QueryResult row : results) {
                  String reason = row.getString("reason");
                  String staff = row.getString("banned_by");
                  long timestamp = row.getLong("expires_at", 0L); // provides default 0 if null
                  
                  log.info(staff + " banned for: " + reason + " until " + timestamp);
              }
          });
    }

    public void stripPunishments(UUID target) {
        AsyncDatabase db = SunDB.getManager().get("main");
        
        // Build a type-safe UPDATE query
        db.updateTable("punishments")
          .set("active", false)
          .set("voided_by", "CONSOLE")
          .where("uuid", "=", target.toString())
          .executeAsync(); // Sends to background Virtual Thread
    }
}
```

---

## Example 8: Production Upgrades (Migrations)
**Use Case:** You released v1.0 of your plugin. Two months later, you release v2.0 which needs a new database column. Using standard `TableBuilder` would fail. Migrations ensure SQL executes linearly over time.

```java
import me.sunmc.db.SunDB;
import me.sunmc.db.api.AsyncDatabase;
import org.bukkit.plugin.java.JavaPlugin;

public class EvolvingPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        AsyncDatabase db = SunDB.getManager().get("main");

        // The Migration Runner tracks execution inside the 'sundb_migrations' system table.
        // It guarantees that scripts are only ever executed exactly once automatically.
        db.migrate("evolving_plugin_schema")
            
            // If they install v1.0, this executes.
            .version(1, """
                CREATE TABLE plugin_data (
                    uuid VARCHAR(36) PRIMARY KEY,
                    playtime INT DEFAULT 0
                )
            """)
            
            // When they later upgrade the jar file to v2.0, SunDB realizes version 2
            // hasn't executed yet, and runs exactly this alter script, leaving data intact.
            .version(2, """
                ALTER TABLE plugin_data ADD COLUMN last_login TIMESTAMP
            """)
            
            // Execute the pipeline
            .execute();
            
        getLogger().info("Database schema is fully up to date!");
    }
}
```
