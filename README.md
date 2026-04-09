# SunDB

[![Release](https://jitpack.io/v/sun-mc-dev/SunDB.svg)](https://jitpack.io/#sun-mc-dev/SunDB)

A lightweight, multi-platform asynchronous database library for modern Minecraft servers. 

Designed specifically for Java 21+, SunDB utilizes native Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`) to handle all database operations off the main thread, ensuring zero TPS impact without the overhead of older scheduler libraries.

## Features

* **Multi-Platform:** Runs natively on **Paper**, **Folia**, and **Velocity**.
* **Thin Universal Jar:** A single compiled `.jar` works across all supported platforms. On Paper/Folia, it uses native library loading. On Velocity, it automatically resolves and injects its own runtime dependencies to keep file size minimal.
* **Modern Async:** Powered entirely by Java 21 Virtual Threads.
* **Connection Pooling:** Uses HikariCP for efficient SQL connection management.

## Supported Databases

* MySQL
* MariaDB
* PostgreSQL
* SQLite
* H2
* Redis (Lettuce)
* MongoDB

## Installation

You can include SunDB in your project via [JitPack](https://jitpack.io).

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
Interact with your database asynchronously using the built-in result wrappers:

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
        
        // Operations are automatically handled via Virtual Threads
        UpdateResult result = database.update(sql, uuid, name, name);
        
        if (result.isSuccess()) {
            System.out.println("Saved " + name + " to database.");
        }
    }

    public void loadPlayer(String uuid) {
        String sql = "SELECT name FROM players WHERE uuid = ?";
        
        QueryResult result = database.query(sql, uuid);
        
        if (result.hasResults()) {
            String name = result.getString("name");
            System.out.println("Loaded player: " + name);
        }
    }
}
```

## Building from Source

To compile the project yourself, clone the repository and build using Maven:

```bash
git clone [https://github.com/sun-mc-dev/SunDB.git](https://github.com/sun-mc-dev/SunDB.git)
cd SunDB
mvn clean package
```
The compiled jar will be located in the `target/` directory.

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