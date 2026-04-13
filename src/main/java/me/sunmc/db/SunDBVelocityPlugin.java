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
import org.bstats.charts.SimplePie;
import org.bstats.velocity.Metrics;

import java.nio.file.Path;

public final class SunDBVelocityPlugin {

    private final ProxyServer server;
    private final Path dataDirectory;
    private final Metrics.Factory metricsFactory;

    @Inject
    public SunDBVelocityPlugin(ProxyServer server, @DataDirectory Path dataDirectory, Metrics.Factory metricsFactory) {
        this.server = server;
        this.dataDirectory = dataDirectory;
        this.metricsFactory = metricsFactory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        SunDBLogger.info("Initializing SunDB for Velocity...");

        try {
            // Libraries are loaded lazily when databases are registered.
            SunDB.init(new VelocityAdapter(this, server, dataDirectory));
            ConfigLoader.load(dataDirectory);

            Metrics metrics = metricsFactory.make(this, 30738);
            metrics.addCustomChart(new SimplePie("registered_databases", () ->
                    String.valueOf(SunDB.getManager().getRegistered().size())));

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
        SunDB.shutdown();
        SunDBLogger.info("SunDB shutdown!!");
    }
}
