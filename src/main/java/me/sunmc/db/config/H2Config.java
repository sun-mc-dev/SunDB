package me.sunmc.db.config;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public record H2Config(Path filePath, boolean inMemory) implements DatabaseConfig {

    @Contract("_ -> new")
    public static @NotNull H2Config file(Path path) {
        return new H2Config(path, false);
    }

    @Contract(" -> new")
    public static @NotNull H2Config memory() {
        return new H2Config(null, true);
    }

    @Contract(pure = true)
    public @NotNull String jdbcUrl() {
        return inMemory
                ? "jdbc:h2:mem:sundb;DB_CLOSE_DELAY=-1;MODE=MySQL"
                : "jdbc:h2:file:" + filePath.toAbsolutePath() + ";MODE=MySQL";
    }
}