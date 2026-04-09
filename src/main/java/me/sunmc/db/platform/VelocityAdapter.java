package me.sunmc.db.platform;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public final class VelocityAdapter implements PlatformAdapter {

    private final Object pluginInstance;

    public VelocityAdapter(Object pluginInstance) {
        this.pluginInstance = pluginInstance;
    }

    public Object plugin() {
        return pluginInstance;
    }

    @Contract(pure = true)
    @Override
    public @NotNull String platformName() {
        return "Velocity";
    }
}