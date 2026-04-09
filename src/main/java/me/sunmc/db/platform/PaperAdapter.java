package me.sunmc.db.platform;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public final class PaperAdapter implements PlatformAdapter {

    private final JavaPlugin plugin;

    public PaperAdapter(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    @Contract(pure = true)
    @Override
    public @NotNull String platformName() {
        return "Paper/Folia";
    }
}