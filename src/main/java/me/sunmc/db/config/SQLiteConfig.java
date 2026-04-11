package me.sunmc.db.config;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.util.List;

public record SQLiteConfig(Path filePath) implements DatabaseConfig {

    @Contract("_ -> new")
    public static @NotNull SQLiteConfig of(Path path) {
        return new SQLiteConfig(path);
    }

    @Contract(pure = true)
    public @NotNull String jdbcUrl() {
        return "jdbc:sqlite:" + filePath.toAbsolutePath();
    }

    @Contract(" -> new")
    @Override
    public @NotNull @Unmodifiable List<MavenLibrary> requiredLibraries() {
        return List.of(
                new MavenLibrary("com.zaxxer", "HikariCP", "7.0.2"),
                new MavenLibrary("org.xerial", "sqlite-jdbc", "3.51.3.0")
        );
    }
}