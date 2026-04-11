package me.sunmc.db.platform;

import me.sunmc.db.config.MavenLibrary;

import java.util.List;

/**
 * Thin abstraction so SunDB core doesn't hard-depend on Paper or Velocity.
 */
public interface PlatformAdapter {

    String platformName();

    /**
     * Load the given Maven libraries into the runtime classpath.
     * On Velocity, this downloads and injects JARs on demand.
     * On Paper, this is a no-op since Paper's plugin.yml handles library loading at startup.
     */
    void loadLibraries(List<MavenLibrary> libraries);
}