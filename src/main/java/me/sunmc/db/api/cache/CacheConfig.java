package me.sunmc.db.api.cache;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Configuration for the in-memory query cache.
 *
 * <pre>{@code
 * CacheConfig config = CacheConfig.builder()
 *     .maxSize(1000)
 *     .expireAfterWrite(Duration.ofMinutes(5))
 *     .build();
 * }</pre>
 */
public record CacheConfig(int maxSize, Duration expireAfterWrite) {

    @Contract(" -> new")
    public static @NotNull Builder builder() {
        return new Builder();
    }

    @Contract(" -> new")
    public static @NotNull CacheConfig defaults() {
        return new CacheConfig(500, Duration.ofMinutes(5));
    }

    public static final class Builder {
        private int maxSize = 500;
        private Duration expireAfterWrite = Duration.ofMinutes(5);

        public Builder maxSize(int maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        public Builder expireAfterWrite(Duration ttl) {
            this.expireAfterWrite = ttl;
            return this;
        }

        @Contract(" -> new")
        public @NotNull CacheConfig build() {
            return new CacheConfig(maxSize, expireAfterWrite);
        }
    }
}
