package me.sunmc.db.api.resilience;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Configuration for {@link ResilientDatabase}.
 *
 * <pre>{@code
 * ResilientConfig config = ResilientConfig.builder()
 *     .failureThreshold(5)           // open circuit after 5 consecutive failures
 *     .circuitTimeout(Duration.ofSeconds(30))  // probe again after 30s
 *     .maxConcurrentOps(300)         // reject if >300 ops in-flight simultaneously
 *     .build();
 *
 * AsyncDatabase db = SunDB.get("main").withResilience(config);
 * }</pre>
 */
public record ResilientConfig(
        int failureThreshold,
        Duration circuitTimeout,
        int maxConcurrentOps
) {
    @Contract(" -> new")
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * Sensible defaults for a 25k-player network.
     */
    @Contract(" -> new")
    public static @NotNull ResilientConfig forLargeNetwork() {
        return new Builder()
                .failureThreshold(5)
                .circuitTimeout(Duration.ofSeconds(30))
                .maxConcurrentOps(500)
                .build();
    }

    /**
     * Conservative defaults for smaller networks.
     */
    @Contract(" -> new")
    public static @NotNull ResilientConfig defaults() {
        return new Builder().build();
    }

    public static final class Builder {
        private int failureThreshold = 5;
        private Duration circuitTimeout = Duration.ofSeconds(30);
        private int maxConcurrentOps = 200;

        public Builder failureThreshold(int v) {
            this.failureThreshold = v;
            return this;
        }

        public Builder circuitTimeout(Duration v) {
            this.circuitTimeout = v;
            return this;
        }

        /**
         * Max simultaneous in-flight DB operations. Excess requests are rejected fast.
         */
        public Builder maxConcurrentOps(int v) {
            this.maxConcurrentOps = v;
            return this;
        }

        @Contract(" -> new")
        public @NotNull ResilientConfig build() {
            return new ResilientConfig(failureThreshold, circuitTimeout, maxConcurrentOps);
        }
    }
}