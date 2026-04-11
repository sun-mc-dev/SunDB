package me.sunmc.db.impl.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import me.sunmc.db.api.AsyncDatabase;
import me.sunmc.db.api.DatabaseMetrics;
import me.sunmc.db.api.TransactionCallback;
import me.sunmc.db.api.TransactionContext;
import me.sunmc.db.api.result.QueryResult;
import me.sunmc.db.api.result.UpdateResult;
import me.sunmc.db.config.PoolConfig;
import me.sunmc.db.util.VirtualExecutor;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

abstract class AbstractSqlDatabase implements AsyncDatabase {

    protected final String name;
    protected volatile HikariDataSource dataSource;

    AbstractSqlDatabase(String name) {
        this.name = name;
    }

    /**
     * Shared ResultSet → QueryResult mapper. Uses {@link LinkedHashMap#newLinkedHashMap(int)}
     * to pre-size correctly (accounts for load factor, avoids rehashing).
     */
    static @NotNull List<QueryResult> mapResultSet(@NotNull ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();
        List<QueryResult> rows = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = LinkedHashMap.newLinkedHashMap(cols);
            for (int i = 1; i <= cols; i++) row.put(meta.getColumnLabel(i), rs.getObject(i));
            rows.add(new QueryResult(row));
        }
        return rows;
    }

    protected abstract String jdbcUrl();

    protected abstract String driverClass();

    protected abstract String username();

    protected abstract String password();

    protected abstract PoolConfig pool();

    @Override
    public String name() {
        return name;
    }

    @Override
    public @NotNull DatabaseMetrics metrics() {
        HikariDataSource ds = this.dataSource;
        if (ds == null || ds.isClosed()) return DatabaseMetrics.unavailable();

        HikariPoolMXBean mxBean = ds.getHikariPoolMXBean();
        if (mxBean == null) return DatabaseMetrics.unavailable();

        return new DatabaseMetrics(
                mxBean.getActiveConnections(),
                mxBean.getIdleConnections(),
                mxBean.getTotalConnections(),
                mxBean.getThreadsAwaitingConnection()
        );
    }

    @Override
    public CompletableFuture<Void> connectAsync() {
        return CompletableFuture.runAsync(() -> {
            synchronized (this) {
                if (dataSource != null && !dataSource.isClosed()) return;

                var cfg = new HikariConfig();
                cfg.setJdbcUrl(jdbcUrl());
                cfg.setDriverClassName(driverClass());

                String u = username(), p = password();
                if (u != null) cfg.setUsername(u);
                if (p != null) cfg.setPassword(p);

                PoolConfig pc = pool();
                cfg.setMaximumPoolSize(pc.maximumPoolSize());
                cfg.setMinimumIdle(pc.minimumIdle());
                cfg.setConnectionTimeout(pc.connectionTimeoutMs());
                cfg.setIdleTimeout(pc.idleTimeoutMs());
                cfg.setMaxLifetime(pc.maxLifetimeMs());
                cfg.setKeepaliveTime(pc.keepAliveTimeMs());
                cfg.setPoolName("SunDB-" + name);

                // Performance tweaks
                cfg.addDataSourceProperty("cachePrepStmts", "true");
                cfg.addDataSourceProperty("prepStmtCacheSize", "250");
                cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                cfg.addDataSourceProperty("useServerPrepStmts", "true");

                dataSource = new HikariDataSource(cfg);
            }
        }, VirtualExecutor.get());
    }

    @Override
    public CompletableFuture<Void> disconnectAsync() {
        return CompletableFuture.runAsync(() -> {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        }, VirtualExecutor.get());
    }

    @Override
    public CompletableFuture<List<QueryResult>> queryAsync(String sql, Object... params) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);

                try (ResultSet rs = ps.executeQuery()) {
                    return mapResultSet(rs);
                }
            } catch (SQLException ex) {
                throw new RuntimeException("Query failed: " + sql, ex);
            }
        }, VirtualExecutor.get());
    }

    @Override
    public CompletableFuture<UpdateResult> updateAsync(String sql, Object... params) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);

                int affected = ps.executeUpdate();
                Object genKey = null;

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) genKey = keys.getObject(1);
                }
                return UpdateResult.of(affected, genKey);
            } catch (SQLException ex) {
                throw new RuntimeException("Update failed: " + sql, ex);
            }
        }, VirtualExecutor.get());
    }

    @Override
    public CompletableFuture<int[]> batchAsync(String sql, List<Object[]> batches) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                conn.setAutoCommit(false);
                try {
                    for (Object[] row : batches) {
                        for (int i = 0; i < row.length; i++) ps.setObject(i + 1, row[i]);
                        ps.addBatch();
                    }
                    int[] result = ps.executeBatch();
                    conn.commit();
                    return result;
                } catch (SQLException ex) {
                    conn.rollback();
                    throw new RuntimeException("Batch failed (rolled back): " + sql, ex);
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (RuntimeException ex) {
                throw ex;
            } catch (SQLException ex) {
                throw new RuntimeException("Batch connection error: " + sql, ex);
            }
        }, VirtualExecutor.get());
    }

    @Override
    public <T> CompletableFuture<T> transactionAsync(TransactionCallback<T> callback) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    T result = callback.execute(new SqlTransactionContext(conn));
                    conn.commit();
                    return result;
                } catch (Exception ex) {
                    conn.rollback();
                    throw new RuntimeException("Transaction rolled back", ex);
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException ex) {
                throw new RuntimeException("Transaction error", ex);
            }
        }, VirtualExecutor.get());
    }

    @Override
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    // Inner context — conn is already open, stay synchronous.
    // Reuses the static mapResultSet() to avoid duplicated logic.
    private record SqlTransactionContext(Connection conn) implements TransactionContext {

        @Override
        public @NotNull List<QueryResult> query(String sql, Object @NotNull ... params) throws Exception {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
                try (ResultSet rs = ps.executeQuery()) {
                    return mapResultSet(rs);
                }
            }
        }

        @Override
        public @NotNull UpdateResult update(String sql, Object @NotNull ... params) throws Exception {
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
                int affected = ps.executeUpdate();
                Object key = null;
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) key = keys.getObject(1);
                }
                return UpdateResult.of(affected, key);
            }
        }
    }
}