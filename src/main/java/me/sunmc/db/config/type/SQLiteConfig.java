package me.sunmc.db.config.type;

import me.sunmc.db.config.DatabaseConfig;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public record SQLiteConfig(Path filePath) implements DatabaseConfig {

    @Contract("_ -> new")
    public static @NotNull SQLiteConfig of(Path path) {
        return new SQLiteConfig(path);
    }

    @Contract(pure = true)
    public @NotNull String jdbcUrl() {
        return "jdbc:sqlite:" + filePath.toAbsolutePath();
    }
}