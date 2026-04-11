package me.sunmc.db.platform;

import com.velocitypowered.api.proxy.ProxyServer;
import me.sunmc.db.config.MavenLibrary;
import me.sunmc.db.util.SunDBLogger;
import me.sunmc.db.util.VelocityLibraryLoader;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class VelocityAdapter implements PlatformAdapter {

    private final Object pluginInstance;
    private final VelocityLibraryLoader loader;
    private final Set<String> loadedLibraries = ConcurrentHashMap.newKeySet();

    public VelocityAdapter(Object pluginInstance, ProxyServer server, Path dataDirectory) {
        this.pluginInstance = pluginInstance;
        this.loader = new VelocityLibraryLoader(server, pluginInstance, dataDirectory);
    }

    public Object plugin() {
        return pluginInstance;
    }

    @Contract(pure = true)
    @Override
    public @NotNull String platformName() {
        return "Velocity";
    }

    /**
     * Downloads and injects only the libraries that haven't been loaded yet.
     * Thread-safe via ConcurrentHashMap-backed Set.
     */
    @Override
    public void loadLibraries(@NotNull List<MavenLibrary> libraries) {
        for (MavenLibrary lib : libraries) {
            if (loadedLibraries.add(lib.key())) {
                SunDBLogger.info("Lazy-loading library: " + lib.artifactId() + "-" + lib.version());
                loader.loadLibrary(lib.groupId(), lib.artifactId(), lib.version());
            }
        }
    }
}