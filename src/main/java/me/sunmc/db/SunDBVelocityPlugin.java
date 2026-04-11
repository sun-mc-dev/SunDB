package me.sunmc.db;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import me.sunmc.db.command.VelocityCommand;
import me.sunmc.db.config.ConfigLoader;
import me.sunmc.db.platform.VelocityAdapter;
import me.sunmc.db.util.SunDBLogger;

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

        try {
            // Libraries are loaded lazily when databases are registered.
            SunDB.init(new VelocityAdapter(this, server, dataDirectory));
            ConfigLoader.load(dataDirectory);

            server.getCommandManager().register(
                    server.getCommandManager().metaBuilder("sundb").aliases("db").build(),
                    new VelocityCommand(dataDirectory)
            );

            SunDBLogger.info("SunDB successfully enabled on Velocity.");
        } catch (Exception e) {
            SunDBLogger.error("Failed to initialize SunDB platform adapter.");
            e.printStackTrace();
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        SunDBLogger.info("SunDB shutting down on Velocity...");
        SunDB.shutdown();
    }
}
