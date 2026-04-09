package me.sunmc.db;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import me.sunmc.db.platform.VelocityAdapter;
import me.sunmc.db.util.SunDBLogger;
import me.sunmc.db.util.VelocityLibraryLoader;

import java.nio.file.Path;

public final class SunDBVelocityPlugin {

    private final ProxyServer server;
    private final Path dataDirectory;

    @Inject
    public SunDBVelocityPlugin(ProxyServer server, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        SunDBLogger.info("Initializing SunDB for Velocity...");

        VelocityLibraryLoader loader = new VelocityLibraryLoader(server, this, dataDirectory);

        loader.loadLibrary("com.zaxxer", "HikariCP", "7.0.2");
        loader.loadLibrary("com.mysql", "mysql-connector-j", "9.6.0");
        loader.loadLibrary("org.mariadb.jdbc", "mariadb-java-client", "3.5.7");
        loader.loadLibrary("org.postgresql", "postgresql", "42.7.10");
        loader.loadLibrary("com.h2database", "h2", "2.4.240");
        loader.loadLibrary("org.xerial", "sqlite-jdbc", "3.51.3.0");

        loader.loadLibrary("io.lettuce", "lettuce-core", "7.5.0.RELEASE");

        loader.loadLibrary("org.mongodb", "mongodb-driver-sync", "5.7.0-beta1");
        loader.loadLibrary("org.mongodb", "mongodb-driver-core", "5.7.0-beta1");
        loader.loadLibrary("org.mongodb", "bson", "5.7.0-beta1");

        // 3. Start the actual database library using 'this'
        try {
            SunDB.init(new VelocityAdapter(this));
            SunDBLogger.info("SunDB successfully enabled on Velocity.");
        } catch (Exception e) {
            SunDBLogger.error("Failed to initialize SunDB platform adapter.");
            e.printStackTrace();
        }
    }
}