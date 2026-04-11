package me.sunmc.db.config;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a single Maven artifact coordinate.
 * Used by {@link DatabaseConfig#requiredLibraries()} to declare which
 * driver JARs a particular database backend needs at runtime.
 */
public record MavenLibrary(
        @NotNull String groupId,
        @NotNull String artifactId,
        @NotNull String version
) {

    /**
     * Unique key used by the platform loader to deduplicate across
     * multiple registrations that share the same dependency (e.g. HikariCP).
     */
    public @NotNull String key() {
        return groupId + ":" + artifactId + ":" + version;
    }
}
