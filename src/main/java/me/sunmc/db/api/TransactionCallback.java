package me.sunmc.db.api;

/**
 * Functional interface for transaction blocks.
 * Throw any exception to trigger a rollback.
 */
@FunctionalInterface
public interface TransactionCallback<T> {
    T execute(TransactionContext ctx) throws Exception;
}
