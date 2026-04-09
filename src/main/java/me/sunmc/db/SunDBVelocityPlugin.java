package me.sunmc.db;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import me.sunmc.db.platform.VelocityAdapter;
import me.sunmc.db.util.SunDBLogger;

public final class SunDBVelocityPlugin {

    private final Object pluginInstance;

    @Inject
    public SunDBVelocityPlugin(Object pluginInstance) {
        this.pluginInstance = pluginInstance;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        SunDB.init(new VelocityAdapter(pluginInstance));
        SunDBLogger.info("SunDB enabled on Velocity.");
    }
}