# SunDB

[![Release](https://jitpack.io/v/sun-mc-dev/SunDB.svg)](https://jitpack.io/#sun-mc-dev/SunDB)

📚 **[Read the Full Documentation](DOCUMENTATION.md)** | 🛠 **[View 8 Real-World Plugin Examples](EXAMPLE_PLUGIN.md)**

A lightweight, multi-platform asynchronous database library for modern Minecraft servers.

Designed specifically for Java 21+, SunDB utilizes native Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`) to handle all database operations off the main thread, ensuring zero TPS impact without the overhead of older scheduler libraries.

## Features

* **Multi-Platform:** Runs natively on **Paper**, **Folia**, and **Velocity**.
* **Thin Universal Jar:** A single compiled `.jar` works across all supported platforms. On Paper/Folia, it uses native library loading. On Velocity, it automatically resolves and injects its own runtime dependencies to keep file size minimal.
* **Modern Async:** Powered entirely by Java 21 Virtual Threads.
* **Connection Pooling:** Uses HikariCP for efficient SQL connection management.
* **Sync & Async APIs:** Every async call has a blocking counterpart for clean virtual-thread code.
* **Typed Query Results:** `getString`, `getInt`, `getUUID`, `getTimestamp`, and more.
* **Fluent Table Builder:** Cross-dialect `CREATE TABLE` without raw DDL.
* **Schema Migrations:** Versioned, idempotent migrations tracked automatically.
* **Player Data Store:** Zero-boilerplate UUID-keyed storage for SQL & Redis.
* **Query Cache:** In-memory TTL cache decorator with auto-invalidation on writes.
* **Redis Pub/Sub:** Cross-server messaging via Lettuce.
* **ORM Entity Mapping:** Map your Java objects directly to database tables via `@Table` and `@Column` annotations.
* **Caching & Optimization**: In-memory caching decorators built with Caffeine.
* **Advanced Metrics**: Monitor your live HikariCP connection pools in-game using the native `/sundb status` command.
* **Fluent Query Builder:** Type-safe SELECT, INSERT, UPDATE, DELETE without raw SQL.

## Supported Databases

* MySQL
* MariaDB
* PostgreSQL
* SQLite
* H2
* Redis (Lettuce)
* MongoDB

## Installation

You can include SunDB in your project via [JitPack](https://jitpack.io/#sun-mc-dev/SunDB).

### Maven

Add the JitPack repository to your `pom.xml`:
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Add the dependency:
```xml
<dependency>
    <groupId>com.github.sun-mc-dev</groupId>
    <artifactId>SunDB</artifactId>
    <version>VERSION</version>
    <scope>provided</scope>
</dependency>
```

### Gradle (build.gradle)

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    compileOnly 'com.github.sun-mc-dev:SunDB:VERSION'
}
```

## Setup & Usage

SunDB operates as a standalone plugin on your server/proxy. To use it in your own plugins, make sure you add `SunDB` as a `depend` or `softdepend` in your `plugin.yml` or `velocity-plugin.json`.

### 1. Configure the Database
When you start the server for the first time with SunDB installed, it will generate a `config.yml`. Select your database type and enter your credentials.

### 2. API Quick Start

Interact with your database using the built-in result wrappers:

```java
import me.sunmc.db.SunDB;
import me.sunmc.db.api.AsyncDatabase;
import me.sunmc.db.api.result.QueryResult;
import me.sunmc.db.api.result.UpdateResult;

public class MyDatabaseHandler {

    private final AsyncDatabase database;

    public MyDatabaseHandler() {
        // Get the active database instance
        this.database = SunDB.getDatabase();
    }

    public void savePlayer(String uuid, String name) {
        String sql = "INSERT INTO players (uuid, name) VALUES (?, ?) ON DUPLICATE KEY UPDATE name = ?";
        
        // Blocking call — safe on virtual threads
        UpdateResult result = database.update(sql, uuid, name, name);
        
        if (result.isSuccess()) {
            System.out.println("Saved " + name + " to database.");
        }
    }

    public void loadPlayer(String uuid) {
        // queryFirst returns a single row or null
        QueryResult result = database.queryFirst("SELECT * FROM players WHERE uuid = ?", uuid);
        
        if (result != null) {
            String name = result.getString("name");
            double balance = result.getDouble("balance", 0.0);
            UUID id = result.getUUID("uuid");
            System.out.println("Loaded player: " + name);
        }
    }
    
    public void loadPlayerAsync(String uuid) {
        // Full async with CompletableFuture
        database.queryFirstAsync("SELECT * FROM players WHERE uuid = ?", uuid)
            .thenAccept(result -> {
                if (result != null) {
                    System.out.println("Loaded: " + result.getString("name"));
                }
            });
    }
}
```

---

## Typed Query Results

`QueryResult` provides typed getters to eliminate manual casting:

```java
QueryResult row = database.queryFirst("SELECT * FROM players WHERE uuid = ?", uuid);

row.getString("name");            // String (nullable)
row.getInt("level");              // int (throws if missing)
row.getInt("level", 1);           // int with default
row.getLong("last_login");        // long
row.getDouble("balance");        // double
row.getDouble("balance", 0.0);   // double with default
row.getFloat("speed");           // float
row.getBoolean("is_banned");     // boolean (handles 0/1 and "true"/"false")
row.getUUID("uuid");             // java.util.UUID (from VARCHAR(36) or native)
row.getTimestamp("created_at");  // java.time.Instant (from SQL Timestamp, epoch millis, or ISO-8601)
row.hasKey("name");              // boolean
row.size();                      // number of columns
row.raw();                       // Map<String, Object>
```

---

## Fluent Table Builder

Eliminate raw DDL SQL. Generates cross-dialect `CREATE TABLE` (MySQL, PostgreSQL, SQLite, H2):

```java
database.table("players")
    .column("uuid", ColumnType.VARCHAR, 36)
    .column("name", ColumnType.VARCHAR, 16)
    .column("balance", ColumnType.DOUBLE).defaultValue(0.0)
    .column("last_login", ColumnType.TIMESTAMP).nullable()
    .column("is_banned", ColumnType.BOOLEAN).defaultValue(false)
    .primaryKey("uuid")
    .index("idx_name", "name")
    .createIfNotExists();
```

Supported column types: `VARCHAR`, `TEXT`, `INT`, `BIGINT`, `DOUBLE`, `FLOAT`, `BOOLEAN`, `TIMESTAMP`, `BLOB`.

---

## Schema Migrations

Upgrade your DB schema between plugin versions without data loss. SunDB tracks applied versions in a `sundb_migrations` table automatically:

```java
database.migrate("my_plugin")
    .version(1, "CREATE TABLE players (uuid VARCHAR(36) PRIMARY KEY, name VARCHAR(16))")
    .version(2, "ALTER TABLE players ADD COLUMN balance DOUBLE DEFAULT 0.0")
    .version(3, "CREATE INDEX idx_name ON players (name)")
    .execute();
```

* Idempotent — safe to call on every startup.
* Only unapplied versions run.
* Fails fast on error with a clear message.

---

## Player Data Store

Zero-boilerplate UUID-keyed storage. Works across SQL and Redis:

```java
PlayerStore store = database.playerStore("my_plugin_data");

// Save
store.set(uuid, "balance", 100.0);
store.set(uuid, "rank", "VIP");

// Load
double balance = store.get(uuid, "balance", Double.class).orElse(0.0);

// Bulk load
Map<String, String> data = store.getAll(uuid);

// Delete
store.delete(uuid);
```

* SQL: auto-creates a key-value table (`store_name`, `uuid`, `key`, `value`).
* Redis: uses hash maps per UUID (`HSET`, `HGET`, `HGETALL`).

---

## Query Cache

Wrap any database with an in-memory TTL cache to reduce round trips:

```java
CachedDatabase cached = database.withCache(CacheConfig.builder()
    .maxSize(1000)
    .expireAfterWrite(Duration.ofMinutes(5))
    .build());

// First call: hits the database
cached.query("SELECT * FROM players WHERE uuid = ?", uuid);

// Second call within 5 min: instant, from memory
cached.query("SELECT * FROM players WHERE uuid = ?", uuid);

// Writes automatically invalidate all cached entries
cached.update("UPDATE players SET name = ? WHERE uuid = ?", name, uuid);
```

---

## Redis Pub/Sub

Cross-server messaging for Velocity/BungeeCord networks:

```java
RedisDatabase redis = (RedisDatabase) SunDB.getDatabase();

// Subscribe to a channel
redis.subscribe("chat_channel", (channel, message) -> {
    System.out.println("[" + channel + "] " + message);
});

// Publish a message
redis.publish("chat_channel", "Hello from server-1!");

// Pattern subscribe
redis.psubscribe("events.*", (channel, message) -> {
    // Matches events.join, events.quit, etc.
});

// Unsubscribe
redis.unsubscribe("chat_channel");
```

---

## Fluent Query Builder

Build type-safe queries without raw SQL strings:

### SELECT
```java
List<QueryResult> results = database.select("players")
    .columns("uuid", "name", "balance")
    .where("balance", ">", 100)
    .and("last_login", ">", Instant.now().minus(Duration.ofDays(30)))
    .orderBy("balance", Order.DESC)
    .limit(10)
    .execute();
```

### INSERT
```java
database.insertInto("players")
    .set("uuid", uuid)
    .set("name", name)
    .set("balance", 0.0)
    .onDuplicateKeyUpdate("name", name)
    .execute();
```

### UPDATE
```java
database.updateTable("players")
    .set("balance", newBalance)
    .where("uuid", "=", uuid)
    .execute();
```

### DELETE
```java
// Safe — throws if no WHERE clause is specified
database.deleteFrom("players")
    .where("uuid", "=", uuid)
    .execute();
```

---

## Building from Source

To compile the project yourself, clone the repository and build using Maven:

```bash
git clone https://github.com/sun-mc-dev/SunDB.git
cd SunDB
mvn clean package
```
The compiled jar will be located in the `target/` directory.

## Credits & Acknowledgements

SunDB acts purely as a bridging API and deeply relies on the incredible open-source work of the following projects. I do not own these technologies, and all rights belong to their respective creators, organizations, and maintainers:

* **[HikariCP](https://github.com/brettwooldridge/HikariCP)** - The high-performance backbone of our SQL connection pooling.
* **[Lettuce](https://lettuce.io/)** - For advanced, non-blocking Redis cluster and Pub/Sub interactions.
* **[MongoDB Java Driver](https://mongodb.github.io/mongo-java-driver/)** - For native Document processing and execution.
* **Database Engines** - [MySQL](https://www.mysql.com/), [MariaDB](https://mariadb.org/), [PostgreSQL](https://www.postgresql.org/), [SQLite](https://www.sqlite.org/), [H2](https://www.h2database.com/), and [Redis](https://redis.io/).

## License
MIT License

Copyright (c) 2026 sun-dev

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.