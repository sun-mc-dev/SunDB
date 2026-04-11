package me.sunmc.db.api.resilience;

/**
 * Thrown when a database call is rejected because the circuit breaker is OPEN.
 */
public final class CircuitOpenException extends RuntimeException {
    public CircuitOpenException(String message) {
        super(message);
    }
}