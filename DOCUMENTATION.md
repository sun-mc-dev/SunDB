# SunDB Developer Documentation

SunDB is a high-performance, asynchronous database library built specifically for modern Minecraft server networks running Java 21+. It supports Paper, Folia, and Velocity out of the box.

This document covers everything from basic queries to schema migrations and pub/sub routing. 

---

## Table of Contents
1. [Core Architecture & Folia Rules](#core-architecture--folia-rules)
2. [Connection Setup](#connection-setup)
   - [Centralized Config (config.yml)](#centralized-config-remote-databases)
   - [Programmatic API (Local Databases)](#programmatic-api-local-databases)
3. [Executing Queries](#executing-queries)
4. [Fluent Query Builders](#fluent-query-builders)
5. [ORM Entity Mapping](#orm-entity-mapping)
6. [Table Creation & Migrations](#schema-management)
7. [Player Data Stores](#player-data-stores)
8. [Caching & Multi-Tier Stores](#caching--multi-tier-stores)
9. [Redis Pub/Sub](#redis-pubsub)
10. [Metrics & Commands](#metrics--commands)

---

## Core Architecture & Folia Rules

SunDB abandons the standard Bukkit/Velocity thread schedulers. Every database operation runs on **Java 21 Virtual Threads**.

**What this means for you:**
* Virtual threads have near-zero overhead. You can have 10,000 queries running at the exact same moment without causing CPU thrashing.
* Every operation in SunDB returns a `CompletableFuture`.
* SunDB provides **Sync** convenience methods (like `db.query()`). These methods run on a virtual thread and block *that specific virtual thread* until the result returns. They do not pin the OS carrier thread.

### The Golden Rule for Folia
If you use Folia, you must be extremely careful with the Sync convenience methods.
* **Bad:** Calling `db.query()` inside an `EntityDamageEvent`. You are sitting on the Region Thread. If you block it waiting for MySQL, that region will freeze.
* **Good:** Calling `db.queryAsync()` inside the event, then using `.thenAccept()` to schedule a callback task back onto the Region Thread.
* **Good:** Calling `db.query()` inside an isolated background task, where blocking doesn't freeze gameplay.

---

## Connection Setup

### Centralized Config (Remote Databases)
For databases that live on remote servers (MySQL, MariaDB, PostgreSQL, Redis, MongoDB), SunDB acts as a central hub.

When SunDB starts, it generates a `plugins/SunDB/config.yml`. Server admins define their connections here.

```yaml
databases:
  global_mysql:
    type: MYSQL
    host: 192.168.1.50
    port: 3306
    database: network_db
    username: root
    password: mypassword
    # Optional: distribute SELECTs across replicas (round-robin)
    readers: ["slave_node_1", "slave_node_2"]
```

In your plugin, you just request the connection by name. You don't need to ask your users for database passwords again.

```java
// Grabs the shared HikariCP connection pool
AsyncDatabase db = SunDB.get("global_mysql");

// If you only have one DB defined, just use:
AsyncDatabase db = SunDB.getDatabase();
```

### Programmatic API (Local Databases)
For file-based databases like SQLite or H2, **do not put them in the central config**. Sharing an SQLite file across 5 different plugins leads to file locking errors and schema clashes.

Instead, create them dynamically in your plugin's `onEnable()`:

```java
Path dbPath = getDataFolder().toPath().resolve("users.db");
DatabaseConfig sqlite = SQLiteConfig.of(dbPath.toString());

SunDB.register("myplugin_sqlite", sqlite);
AsyncDatabase db = SunDB.get("myplugin_sqlite");
```

---

## Executing Queries

SunDB supports raw SQL execution, but strips away JDBC boilerplate entirely.

### Sync vs Async
Every core action has an `Async` and a blocking equivalent.

```java
// Async: Returns a CompletableFuture
db.queryAsync("SELECT * FROM users WHERE rank = ?", "VIP")
  .thenAccept(results -> {
      System.out.println("Found " + results.size() + " VIPs");
  });

// Sync: Blocks the current thread until finished
List<QueryResult> results = db.query("SELECT * FROM users WHERE rank = ?", "VIP");
```

### Typed Results
The `QueryResult` object completely eliminates manual Java casting. It handles `null` values gracefully by returning default primitives.

```java
QueryResult row = db.queryFirst("SELECT * FROM players WHERE uuid = ?", uuid);

if (row != null) {
    String name    = row.getString("name");
    int coins      = row.getInt("coins", 0);      // Returns 0 if null/missing
    double ratio   = row.getDouble("kdr", 0.0);
    UUID id        = row.getUUID("uuid");
    Instant login  = row.getTimestamp("last_login");
}
```

### Transactions & Batches

**Transactions** allow you to bundle multiple queries. If any query throws an exception, the entire block drops and rolls back.

```java
db.transaction(conn -> {
    conn.update("UPDATE economy SET bal = bal - 100 WHERE uuid = ?", sender);
    
    // Support for Savepoints (Nested Transactions)
    Object sp = conn.setSavepoint();
    try {
        conn.update("UPDATE sub_table SET val = ? WHERE ...", val);
    } catch (Exception e) {
        conn.rollback(sp); // Only rolls back 'sub_table' update
    }
    
    conn.update("UPDATE economy SET bal = bal + 100 WHERE uuid = ?", target);
    return true; // Commit remaining
});
```

**Batches** are for mass inserts. They send data to the database in a single network trip.

```java
List<Object[]> batchData = List.of(
    new Object[]{uuid1, "Notch"},
    new Object[]{uuid2, "jeb_"}
);

// Executes: INSERT INTO users VALUES (uuid1, 'Notch'), (uuid2, 'jeb_')
int[] rowsAffected = db.batch("INSERT INTO users (uuid, name) VALUES (?, ?)", batchData);
```

---

## Fluent Query Builders

If you hate writing raw SQL strings, use the fluent builders. They are sanitized automatically, preventing SQL injection on table/column names.

*Note: Fluent SQL builders throw an error if used against MongoDB or Redis instances.*

### Select
```java
List<QueryResult> topPlayers = db.select("players")
    .columns("uuid", "name", "kills")
    .where("kills", ">", 1000)
    .and("banned", "=", false)
    .orderBy("kills", SelectBuilder.Order.DESC)
    .limit(10)
    .execute();
```

### Insert & Upsert
The `InsertBuilder` automatically generates cross-dialect upsert syntax (`ON DUPLICATE KEY UPDATE` for MySQL, `ON CONFLICT DO UPDATE` for Postgres/SQLite).

```java
db.insertInto("economy")
    .set("uuid", playerUuid)
    .set("balance", 500)
    .onDuplicateKeyUpdate("balance", 500) // Adds to existing if row exists
    .execute();
```

### Update
```java
db.updateTable("players")
    .set("rank", "ADMIN")
    .where("name", "=", "sunmi")
    .execute();
```

### Delete
```java
db.deleteFrom("bans")
    .where("expires_at", "<", Instant.now())
    .execute();
```

---

## ORM Entity Mapping

For structured data like Guilds or User profiles, you can bypass the query builders entirely and use SunDB's reflection-based ORM system.

Simply annotate your plain Java class:

```java
import me.sunmc.db.api.orm.Table;
import me.sunmc.db.api.orm.Id;
import me.sunmc.db.api.orm.Column;
import java.util.UUID;

@Table("users")
public class User {
    @Id @Column("uuid")
    public UUID id;
    
    @Column("name")
    public String name;
    
    @Column("kills")
    public int kills;
}
```

Then, wrap it in a `Repository`:

```java
Repository<User> repo = new Repository<>(db, User.class);

// Performs an INSERT ... ON DUPLICATE KEY UPDATE automatically!
repo.save(myUser);

// Performs a SELECT ... WHERE uuid = ? mapped safely back to your object
User u = repo.findById(uuid).orElse(null);
```

### Relationship Mapping
SunDB supports complex entity associations via `@ManyToOne` and `@OneToMany`.

```java
@Table("posts")
public class Post {
    @Id @Column("id") public int id;
    @Column("content") public String content;
    
    @ManyToOne 
    public User author; // Automatically fetched by Author ID
}

@Table("users")
public class User {
    @Id @Column("uuid") public UUID id;
    
    @OneToMany(mappedBy = "author_id")
    public List<Post> posts; // Lazy-loaded only when accessed!

    @OneToOne(mappedBy = "user_id")
    public UserProfile profile; // Single inverse relationship
}
```

### Relationship Types

*   **@ManyToOne**: The entity has a column `field_id` pointing to the related entity.
*   **@OneToMany(mappedBy="...")**: A collection associated with multiple child entities (Lazy-loaded).
*   **@OneToOne**: 
    *   **Owning Side**: (No `mappedBy`) Table contains a `field_id` foreign key.
    *   **Inverse Side**: (`mappedBy="owner_field"`) Queries the other table's foreign key to find the single matching row.

**Lazy Loading:** Collections annotated with `@OneToMany` use `LazyList`. The database will not be queried for the collection until you actually call a method like `user.posts.size()` or iterate over it. This drastically improves performance for large entity graphs.

---

## Schema Management

Stop writing `CREATE TABLE IF NOT EXISTS` strings. The `TableBuilder` detects what database you are using (MySQL, SQLite, etc.) and translates your Java columns into the direct DDL dialect required.

### TableBuilder
```java
db.table("gang_members")
    .column("id", ColumnType.INT, 0).autoIncrement().primaryKey()
    .column("uuid", ColumnType.VARCHAR, 36)
    .column("gang_id", ColumnType.INT, 0)
    .column("join_date", ColumnType.TIMESTAMP, 0)
    .index("idx_gang_uuid", "gang_id", "uuid") 
    .createIfNotExists();
```

### Migrations
For production plugins, you should use schema migrations instead of raw table creation. This ensures that when you update your plugin in the future and need to add a column, it applies safely without wiping data.

SunDB creates a `sundb_migrations` tracking table automatically. It runs versions sequentially.

```java
db.migrate("my_plugin")
    // Version 1 gets applied on fresh installs
    .version(1, """
        CREATE TABLE stats (
            uuid VARCHAR(36) PRIMARY KEY,
            kills INT DEFAULT 0
        )
    """)
    // Version 2 gets applied when a server updates your plugin jar
    .version(2, "ALTER TABLE stats ADD COLUMN deaths INT DEFAULT 0")
    .execute();
```

---

## Player Data Stores

If you just need to save basic data per player (like settings or stats), defining schemas is overkill. Use a `PlayerStore`. It is a zero-boilerplate, NoSQL-style Key/Value store.

The best part? It uses the same interface whether your backend is MySQL, Redis, or MongoDB. SunDB translates it under the hood.

```java
PlayerStore store = db.playerStore("player_settings");

// Saving data
store.set(uuid, "chat_color", "&c");
store.set(uuid, "show_particles", true);

// Retrieving data
boolean showsParticles = store.get(uuid, "show_particles", Boolean.class).orElse(true);

// Get everything for a player as a Map
Map<String, String> allSettings = store.getAll(uuid);

// Delete a single key or wipe the player
store.remove(uuid, "chat_color");
store.delete(uuid);
```

---

## Caching & Multi-Tier Stores

### MultiPlayerStore (Write-Through Cache)
If you want the speed and cross-server syncing of Redis combined with the permanent storage of MySQL, use a `MultiPlayerStore`.

```java
AsyncDatabase mysqlDB = SunDB.get("main");
AsyncDatabase redisDB = SunDB.get("cache");

// Automatically links the two!
PlayerStore store = mysqlDB.multiStore(redisDB, "player_settings");

// This instantly writes to Redis locally, and spawns a background thread
// to write the same data natively to MySQL. 
store.set(uuid, "theme", "dark");
```

### Cached Database Decorator

You can optionally wrap any full `AsyncDatabase` with a `CachedDatabase` decorator to cache raw query result objects.

This caches identical `SELECT` query results in RAM using Caffeine. If a write operation (UPDATE/INSERT/DELETE) hits a table, the cache automatically invalidates all stored SELECT queries covering that specific table.

```java
// Setup the cache rule: Keep max 10,000 queries memory, expire after 5 minutes
CacheConfig config = CacheConfig.builder()
    .maxSize(10000)
    .expireAfterWrite(Duration.ofMinutes(5))
    .build();

// Wrap your existing database
AsyncDatabase cachedDb = db.withCache(config);

// This hits the real database
cachedDb.query("SELECT * FROM clans WHERE tag = ?", "VIP");

// This returns instantly from RAM
cachedDb.query("SELECT * FROM clans WHERE tag = ?", "VIP");

// This drops the cached results, meaning the next SELECT will hit the real DB again
cachedDb.update("INSERT INTO clans ...");
```

---

## Redis Pub/Sub

If your database is a `RedisDatabase`, you get access to Pub/Sub out of the box. This is critical for cross-server communication between your Velocity proxy and Folia backend servers.

Because `AsyncDatabase` masks the exact type, cast it to `RedisDatabase` first.

```java
AsyncDatabase rawDb = SunDB.get("cache");

if (rawDb.type() == DatabaseType.REDIS) {
    RedisDatabase redis = (RedisDatabase) rawDb;
    
    // Listen for messages from other servers
    redis.subscribe("network_alerts", (channel, message) -> {
        System.out.println("Received alert: " + message);
    });
    
    // Broadcast a message to the entire network
    redis.publish("network_alerts", "Server-1 is rebooting!");
}
```

---

## Metrics & Commands

SunDB hooks directly into internal `HikariCP` and `Apache Commons Pool` states to expose live telemetry for enterprise networks.

### In-Game Commands

SunDB registers a `/sundb` command on both Paper and Velocity. Any sender with the `sundb.admin` permission can run the following subcommands:

| Command             | Description                                                                                                                                                                           |
|---------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `/sundb status`     | Displays real-time connection pool metrics (active, idle, waiting).                                                                                                                   |
| `/sundb top`        | Shows the most recent slow queries recorded by the Query Profiler. Threshold is configurable in `config.yml`.                                                                         |
| `/sundb migrate-db` | Performs a cross-database export, copying tables and data from a source database to a target database.                                                                                |
| `/sundb reload`     | Gracefully closes all database connections defined in `config.yml`, clears the registry, and re-applies the config file. Useful for credential changes without restarting the server. |

> **Important:** `/sundb reload` only restores databases defined in `config.yml`. Databases registered programmatically by individual plugins (e.g. a plugin's private SQLite file via `SunDB.register()`) are **not** affected — those plugins manage their own connection lifecycle via `onEnable` / `onDisable`.

### Developer API

You can fetch live connection metrics yourself to push to your own Prometheus endpoints or debug lag:

```java
DatabaseMetrics metrics = db.metrics();
System.out.println("Active queries:   " + metrics.activeConnections());
System.out.println("Idle connections: " + metrics.idleConnections());
System.out.println("Waiting threads:  " + metrics.threadsAwaitingConnection());
```

You can also trigger a reload programmatically — for example, from your own plugin's admin command:

```java
// Reloads using the last known data folder (set automatically on first load)
SunDB.reload();

// Or pass an explicit path if calling before SunDB has stored one
SunDB.reload(getDataFolder().toPath());
```