package me.sunmc.db.api;

import org.jetbrains.annotations.NotNull;

/**
 * Snapshot of current database connection pool metrics.
 * Safe to call frequently.
 */
public record DatabaseMetrics(
        int activeConnections,
        int idleConnections,
        int totalConnections,
        int threadsAwaitingConnection
) {
    /**
     * Default placeholder when metrics are unavailable (e.g. database not connected
     * or driver doesn't support metrics exposure).
     */
    public static @NotNull DatabaseMetrics unavailable() {
        return new DatabaseMetrics(0, 0, 0, 0);
    }
}
