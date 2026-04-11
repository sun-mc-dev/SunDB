package me.sunmc.db.api.resilience;

/**
 * Thrown when the max concurrent operations limit has been reached.
 */
public final class BackpressureException extends RuntimeException {
    public BackpressureException(String message) {
        super(message);
    }
}