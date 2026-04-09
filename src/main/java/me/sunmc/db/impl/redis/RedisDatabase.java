package me.sunmc.db.impl.redis;

import io.lettuce.core.LettuceFutures;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.support.ConnectionPoolSupport;
import me.sunmc.db.api.AsyncDatabase;
import me.sunmc.db.api.DatabaseType;
import me.sunmc.db.api.TransactionCallback;
import me.sunmc.db.api.TransactionContext;
import me.sunmc.db.api.result.QueryResult;
import me.sunmc.db.api.result.UpdateResult;
import me.sunmc.db.config.type.RedisConfig;
import me.sunmc.db.util.VirtualExecutor;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Redis implementation via Lettuce with a synchronous pooled connection.
 * Runs on virtual threads — IO-bound blocking is fine here.
 * <p>
 * Query  → GET key (params[0] = key). Returns single QueryResult {key: value}.
 * Query  → HGETALL key when params[1] = "hash".
 * Update → SET key value [EX seconds] via params: key, value [, "EX", seconds].
 * Update → DEL key via params: key, "DEL".
 * Update → HSET key field value via params: key, "HSET", field, value.
 */
public final class RedisDatabase implements AsyncDatabase {

    private final String name;
    private final RedisConfig cfg;

    private RedisClient lettuceClient;
    private GenericObjectPool<StatefulRedisConnection<String, String>> pool;

    public RedisDatabase(String name, RedisConfig cfg) {
        this.name = name;
        this.cfg = cfg;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public DatabaseType type() {
        return DatabaseType.REDIS;
    }

    @Contract(" -> new")
    @Override
    public @NotNull CompletableFuture<Void> connectAsync() {
        return CompletableFuture.runAsync(() -> {
            var builder = RedisURI.builder()
                    .withHost(cfg.host())
                    .withPort(cfg.port())
                    .withDatabase(cfg.database())
                    .withSsl(cfg.ssl());

            if (cfg.password() != null && !cfg.password().isBlank())
                builder.withPassword(cfg.password().toCharArray());

            lettuceClient = RedisClient.create(builder.build());

            var poolCfg = new GenericObjectPoolConfig<StatefulRedisConnection<String, String>>();
            poolCfg.setMaxTotal(cfg.maxConnections());
            poolCfg.setMinIdle(1);
            poolCfg.setMaxIdle(cfg.maxConnections());
            poolCfg.setTestOnBorrow(true);

            pool = ConnectionPoolSupport.createGenericObjectPool(
                    () -> lettuceClient.connect(), poolCfg);
        }, VirtualExecutor.get());
    }

    @Contract(" -> new")
    @Override
    public @NotNull CompletableFuture<Void> disconnectAsync() {
        return CompletableFuture.runAsync(() -> {
            if (pool != null) pool.close();
            if (lettuceClient != null) lettuceClient.shutdown();
        }, VirtualExecutor.get());
    }

    @Contract("_, _ -> new")
    @Override
    public @NotNull CompletableFuture<List<QueryResult>> queryAsync(String statement, Object... params) {
        return CompletableFuture.supplyAsync(() -> {
            try (var conn = pool.borrowObject()) {
                RedisCommands<String, String> cmd = conn.sync();
                String key = params.length > 0 ? params[0].toString() : statement;
                boolean isHash = params.length > 1 && "hash".equalsIgnoreCase(params[1].toString());

                if (isHash) {
                    Map<String, String> hash = cmd.hgetall(key);
                    return List.of(new QueryResult(new HashMap<>(hash)));
                }

                String val = cmd.get(key);
                if (val == null) return List.of();
                return List.of(new QueryResult(Map.of(key, val)));
            } catch (Exception ex) {
                throw new RuntimeException("Redis query failed", ex);
            }
        }, VirtualExecutor.get());
    }

    @Contract("_, _ -> new")
    @Override
    public @NotNull CompletableFuture<UpdateResult> updateAsync(String statement, Object... params) {
        return CompletableFuture.supplyAsync(() -> {
            try (var conn = pool.borrowObject()) {
                RedisCommands<String, String> cmd = conn.sync();

                if (params.length >= 2 && "DEL".equalsIgnoreCase(params[1].toString())) {
                    long del = cmd.del(params[0].toString());
                    return UpdateResult.of(del);
                }

                if (params.length >= 4 && "HSET".equalsIgnoreCase(params[1].toString())) {
                    long set = cmd.hset(params[0].toString(), params[2].toString(), params[3].toString());
                    return UpdateResult.of(set);
                }

                // SET key value [EX seconds]
                String key = params[0].toString();
                String val = params[1].toString();

                if (params.length >= 4 && "EX".equalsIgnoreCase(params[2].toString())) {
                    cmd.setex(key, Long.parseLong(params[3].toString()), val);
                } else {
                    cmd.set(key, val);
                }
                return UpdateResult.of(1);
            } catch (Exception ex) {
                throw new RuntimeException("Redis update failed", ex);
            }
        }, VirtualExecutor.get());
    }

    @Contract("_, _ -> new")
    @Override
    public @NotNull CompletableFuture<int[]> batchAsync(String statement, List<Object[]> paramBatches) {
        return CompletableFuture.supplyAsync(() -> {
            try (var conn = pool.borrowObject()) {
                var pipeline = conn.async();
                List<RedisFuture<String>> futures = new ArrayList<>();
                for (Object[] p : paramBatches) {
                    if (p.length >= 2) futures.add(pipeline.set(p[0].toString(), p[1].toString()));
                }
                LettuceFutures.awaitAll(Duration.ofSeconds(10),
                        futures.toArray(new RedisFuture[0]));
                return new int[]{futures.size()};
            } catch (Exception ex) {
                throw new RuntimeException("Redis batch failed", ex);
            }
        }, VirtualExecutor.get());
    }

    @Contract("_ -> new")
    @Override
    public <T> @NotNull CompletableFuture<T> transactionAsync(TransactionCallback<T> callback) {
        return CompletableFuture.supplyAsync(() -> {
            try (var conn = pool.borrowObject()) {
                RedisCommands<String, String> cmd = conn.sync();
                cmd.multi();
                try {
                    T result = callback.execute(new RedisTransactionContext(cmd));
                    cmd.exec();
                    return result;
                } catch (Exception ex) {
                    cmd.discard();
                    throw new RuntimeException("Redis transaction discarded", ex);
                }
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }, VirtualExecutor.get());
    }

    @Override
    public boolean isConnected() {
        return pool != null && !pool.isClosed();
    }

    private record RedisTransactionContext(RedisCommands<String, String> cmd) implements TransactionContext {
        @Override
        public @Unmodifiable List<QueryResult> query(String key, Object... params) {
            String val = cmd.get(key);
            if (val == null) return List.of();
            return List.of(new QueryResult(Map.of(key, val)));
        }

        @Override
        public @NotNull UpdateResult update(String key, Object @NotNull ... params) {
            if (params.length >= 1) cmd.set(key, params[0].toString());
            return UpdateResult.of(1);
        }
    }
}