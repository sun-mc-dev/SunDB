package me.sunmc.db.config;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * HikariCP tuning defaults — sensible for Minecraft plugin workloads.
 */
public record PoolConfig(
        int maximumPoolSize,
        int minimumIdle,
        long connectionTimeoutMs,
        long idleTimeoutMs,
        long maxLifetimeMs,
        long keepAliveTimeMs
) {
    @Contract(" -> new")
    public static @NotNull PoolConfig defaults() {
        return new PoolConfig(10, 2, 5000, 600_000, 1_800_000, 60_000);
    }

    @Contract(" -> new")
    public static @NotNull PoolConfig light() {
        return new PoolConfig(4, 1, 3000, 300_000, 900_000, 30_000);
    }
}
