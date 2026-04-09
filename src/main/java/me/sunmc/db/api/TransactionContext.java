package me.sunmc.db.api;

import me.sunmc.db.api.result.QueryResult;
import me.sunmc.db.api.result.UpdateResult;

import java.util.List;

/**
 * Synchronous context available inside a transaction block.
 * Never use async calls here — the transaction is already on a virtual thread.
 */
public interface TransactionContext {
    List<QueryResult> query(String sql, Object... params) throws Exception;

    UpdateResult update(String sql, Object... params) throws Exception;
}