package me.sunmc.db.config;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.util.List;

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

    @Contract(" -> new")
    @Override
    public @NotNull @Unmodifiable List<MavenLibrary> requiredLibraries() {
        return List.of(
                new MavenLibrary("com.zaxxer", "HikariCP", "7.0.2"),
                new MavenLibrary("com.h2database", "h2", "2.4.240")
        );
    }
}