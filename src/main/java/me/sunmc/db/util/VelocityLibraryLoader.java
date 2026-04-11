package me.sunmc.db.util;

import com.velocitypowered.api.proxy.ProxyServer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class VelocityLibraryLoader {

    private final ProxyServer server;
    private final Object pluginInstance;
    private final Path libsFolder;

    @Contract(pure = true)
    public VelocityLibraryLoader(ProxyServer server, Object pluginInstance, @NotNull Path dataDirectory) {
        this.server = server;
        this.pluginInstance = pluginInstance;
        // This will create a 'libs' folder inside 'plugins/sundb/'
        this.libsFolder = dataDirectory.resolve("libs");
    }

    /**
     * Downloads a library from Maven Central and injects it into the plugin's classpath.
     */
    public void loadLibrary(@NotNull String groupId, @NotNull String artifactId, @NotNull String version) {
        String jarName = artifactId + "-" + version + ".jar";
        Path jarPath = libsFolder.resolve(jarName);

        if (!Files.exists(jarPath)) {
            SunDBLogger.info("Downloading missing library for Velocity: " + jarName + "...");
            try {
                Files.createDirectories(libsFolder);
                String groupPath = groupId.replace('.', '/');
                String url = String.format("https://repo1.maven.org/maven2/%s/%s/%s/%s",
                        groupPath, artifactId, version, jarName);

                try (InputStream in = URI.create(url).toURL().openStream()) {
                    Files.copy(in, jarPath, StandardCopyOption.REPLACE_EXISTING);
                }
                SunDBLogger.info("Successfully downloaded " + jarName);
            } catch (Exception e) {
                SunDBLogger.error("Failed to download " + jarName + " from Maven Central. Database connections may fail.");
                e.printStackTrace();
                return;
            }
        }

        try {
            server.getPluginManager().addToClasspath(pluginInstance, jarPath);
        } catch (Exception e) {
            SunDBLogger.error("Failed to inject " + jarName + " into Velocity classpath.");
            e.printStackTrace();
        }
    }
}