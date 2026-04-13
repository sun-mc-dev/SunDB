package me.sunmc.db;

import me.sunmc.db.command.PaperCommand;
import me.sunmc.db.config.ConfigLoader;
import me.sunmc.db.platform.PaperAdapter;
import me.sunmc.db.util.SunDBLogger;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class SunDBPlugin extends JavaPlugin {

    private static SunDBPlugin instance;

    public static SunDBPlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        SunDB.init(new PaperAdapter(this));
        ConfigLoader.load(getDataFolder().toPath());

        Metrics metrics = new Metrics(this, 30737);
        metrics.addCustomChart(new SimplePie("registered_databases", () ->
                String.valueOf(SunDB.getManager().getRegistered().size())));

        Objects.requireNonNull(getCommand("sundb"))
                .setExecutor(new PaperCommand(this));

        final String version = getPluginMeta().getVersion();
        SunDBLogger.info("SunDB " + version + " enabled.");
    }

    @Override
    public void onDisable() {
        SunDB.shutdown();
        SunDBLogger.info("SunDB shut down.");
    }
}