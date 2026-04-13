package me.sunmc.db.util;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Tiered executors for database operations.
 * <p>
 * Why not one shared virtual thread executor?
 * <p>
 * Virtual threads are scheduled onto a finite pool of OS "carrier" threads.
 * If hundreds of BACKGROUND operations (e.g. leaderboard queries doing 50ms DB
 * calls each) are submitted simultaneously, they can saturate the carrier pool
 * and introduce scheduling latency for CRITICAL player-facing operations.
 * <p>
 * The fix: CRITICAL and NORMAL use unbounded virtual threads (fast, lightweight).
 * BACKGROUND uses a bounded platform thread pool — naturally rate-limiting bulk
 * work and preventing it from starving critical operations.
 * <p>
 * Usage:
 * <pre>{@code
 * // In an event handler — player join, economy, punishment check:
 * db.queryAsync(PriorityExecutor.Priority.CRITICAL, sql, uuid)
 *   .thenAccept(result -> { ... });
 *
 * // Heavy leaderboard rebuild running every 5 minutes:
 * db.queryAsync(PriorityExecutor.Priority.BACKGROUND, heavySql)
 *   .thenAccept(result -> { ... });
 * }</pre>
 */
public final class PriorityExecutor {

    private static final Executor CRITICAL = Executors.newVirtualThreadPerTaskExecutor();
    private static final Executor NORMAL = Executors.newVirtualThreadPerTaskExecutor();
    private static final Executor BACKGROUND = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
            Thread.ofPlatform().name("sundb-bg-", 0).daemon(true).factory()
    );

    private PriorityExecutor() {
    }

    public static Executor get(@NotNull Priority priority) {
        return switch (priority) {
            case CRITICAL -> CRITICAL;
            case NORMAL -> NORMAL;
            case BACKGROUND -> BACKGROUND;
        };
    }

    /**
     * Shorthand for {@code get(Priority.NORMAL)}.
     * Called by SunDB internals — keeps existing code unchanged.
     */
    public static Executor get() {
        return NORMAL;
    }

    public enum Priority {
        /**
         * Player-facing operations that must complete with minimal latency.
         * Examples: join/quit data load, economy transfers, punishment checks.
         * Never queued — always gets a virtual thread immediately.
         */
        CRITICAL,

        /**
         * Standard plugin queries. The default for all SunDB internals.
         * Uses virtual threads — effectively unlimited concurrency.
         */
        NORMAL,

        /**
         * Bulk or analytics operations that can tolerate delay.
         * Examples: leaderboard rebuilds, cache warming, stats aggregation.
         * Uses a bounded platform thread pool — naturally rate-limited, so it
         * cannot starve CRITICAL or NORMAL work during traffic spikes.
         */
        BACKGROUND
    }
}