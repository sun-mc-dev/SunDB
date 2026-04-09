package me.sunmc.db;

import me.sunmc.db.platform.PaperAdapter;
import me.sunmc.db.util.SunDBLogger;
import org.bukkit.plugin.java.JavaPlugin;

public final class SunDBPlugin extends JavaPlugin {

    private static SunDBPlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        SunDB.init(new PaperAdapter(this));
        SunDBLogger.info("SunDB 0.1-alpha enabled.");
    }

    @Override
    public void onDisable() {
        SunDB.shutdown();
        SunDBLogger.info("SunDB shut down.");
    }

    public static SunDBPlugin get() {
        return instance;
    }
}