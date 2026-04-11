package me.sunmc.db.config;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * HikariCP tuning parameters.
 * <p>
 * For large networks, use {@link #forScale(int)} rather than {@link #defaults()}.
 * The default pool size of 10 will cause severe connection queuing under load.
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
        return new PoolConfig(10, 2, 5_000, 600_000, 1_800_000, 60_000);
    }

    @Contract(" -> new")
    public static @NotNull PoolConfig light() {
        return new PoolConfig(4, 1, 3_000, 300_000, 900_000, 30_000);
    }

    /**
     * SQLite uses a single-connection pool — the file lock only allows one writer.
     */
    @Contract(" -> new")
    public static @NotNull PoolConfig sqlite() {
        return new PoolConfig(1, 1, 5_000, 0, 0, 0);
    }

    /**
     * Scale-aware pool sizing for production networks.
     * <p>
     * Formula: 1 connection per ~250 expected concurrent players, capped at 100.
     * This is a starting point — monitor {@code /sundb status} and increase
     * {@code maximumPoolSize} if {@code threadsAwaitingConnection} is consistently > 0.
     * <p>
     * Example targets:
     * <pre>
     *   forScale(1_000)  → pool size  4  (small server)
     *   forScale(5_000)  → pool size 20  (medium network)
     *   forScale(10_000) → pool size 40  (large network)
     *   forScale(25_000) → pool size 100 (capped — tune DB server accordingly)
     * </pre>
     * <p>
     * At 100 connections, ensure your MySQL server has:
     * max_connections >= 150  (headroom for admin tools and replicas)
     * innodb_buffer_pool_size >= 4GB  (for a 25k-player dataset)
     *
     * @param expectedConcurrentPlayers peak simultaneous online players
     */
    @Contract("_ -> new")
    public static @NotNull PoolConfig forScale(int expectedConcurrentPlayers) {
        int poolSize = Math.min(100, Math.max(10, expectedConcurrentPlayers / 250));
        int minIdle = Math.max(2, poolSize / 5);
        return new PoolConfig(
                poolSize,
                minIdle,
                10_000,    // 10s timeout — longer than default to handle brief DB spikes
                600_000,
                1_800_000,
                60_000
        );
    }

    /**
     * Builder for fine-grained manual tuning.
     */
    @Contract(" -> new")
    public static @NotNull Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int maximumPoolSize = 10;
        private int minimumIdle = 2;
        private long connectionTimeout = 5_000;
        private long idleTimeout = 600_000;
        private long maxLifetime = 1_800_000;
        private long keepAliveTime = 60_000;

        public Builder maximumPoolSize(int v) {
            this.maximumPoolSize = v;
            return this;
        }

        public Builder minimumIdle(int v) {
            this.minimumIdle = v;
            return this;
        }

        public Builder connectionTimeout(long v) {
            this.connectionTimeout = v;
            return this;
        }

        public Builder idleTimeout(long v) {
            this.idleTimeout = v;
            return this;
        }

        public Builder maxLifetime(long v) {
            this.maxLifetime = v;
            return this;
        }

        public Builder keepAliveTime(long v) {
            this.keepAliveTime = v;
            return this;
        }

        public @NotNull PoolConfig build() {
            return new PoolConfig(maximumPoolSize, minimumIdle,
                    connectionTimeout, idleTimeout, maxLifetime, keepAliveTime);
        }
    }
}