package me.sunmc.db.impl.mango;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import me.sunmc.db.api.AsyncDatabase;
import me.sunmc.db.api.DatabaseType;
import me.sunmc.db.api.TransactionCallback;
import me.sunmc.db.api.TransactionContext;
import me.sunmc.db.api.result.QueryResult;
import me.sunmc.db.api.result.UpdateResult;
import me.sunmc.db.config.type.MongoConfig;
import me.sunmc.db.util.VirtualExecutor;
import org.bson.Document;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * MongoDB's implementation using the synchronous driver on virtual threads.
 * The driver manages its own internal connection pool (MongoClient).
 * <p>
 * Query format: collection name as statement, params as key-value pairs (k, v, k, v...).
 * Update format: "collection|upsert" or "collection|delete" as statement.
 */
public final class MongoDatabase implements AsyncDatabase {

    private final String name;
    private final MongoConfig cfg;
    private volatile MongoClient client;
    private volatile com.mongodb.client.MongoDatabase db;

    public MongoDatabase(String name, MongoConfig cfg) {
        this.name = name;
        this.cfg = cfg;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public DatabaseType type() {
        return DatabaseType.MONGODB;
    }

    @Contract(" -> new")
    @Override
    public @NotNull CompletableFuture<Void> connectAsync() {
        return CompletableFuture.runAsync(() -> {
            client = MongoClients.create(cfg.connectionString());
            db = client.getDatabase(cfg.database());
        }, VirtualExecutor.get());
    }

    @Contract(" -> new")
    @Override
    public @NotNull CompletableFuture<Void> disconnectAsync() {
        return CompletableFuture.runAsync(() -> {
            if (client != null) client.close();
        }, VirtualExecutor.get());
    }

    @Contract("_, _ -> new")
    @Override
    public @NotNull CompletableFuture<List<QueryResult>> queryAsync(String collection, Object... params) {
        return CompletableFuture.supplyAsync(() -> {
            Document filter = buildFilter(params);
            List<QueryResult> results = new ArrayList<>();
            for (Document doc : db.getCollection(collection).find(filter)) {
                results.add(new QueryResult(doc));
            }
            return results;
        }, VirtualExecutor.get());
    }

    @Contract("_, _ -> new")
    @Override
    public @NotNull CompletableFuture<UpdateResult> updateAsync(String statement, Object... params) {
        return CompletableFuture.supplyAsync(() -> {
            String[] parts = statement.split("\\|", 2);
            String collection = parts[0];
            String op = parts.length > 1 ? parts[1] : "upsert";

            Document doc = buildFilter(params);
            var col = db.getCollection(collection);

            return switch (op) {
                case "delete" -> {
                    long deleted = col.deleteMany(doc).getDeletedCount();
                    yield UpdateResult.of(deleted);
                }
                default -> {
                    var result = col.insertOne(doc);
                    yield UpdateResult.of(1, result.getInsertedId());
                }
            };
        }, VirtualExecutor.get());
    }

    @Contract("_, _ -> new")
    @Override
    public @NotNull CompletableFuture<int[]> batchAsync(String statement, List<Object[]> paramBatches) {
        return CompletableFuture.supplyAsync(() -> {
            var col = db.getCollection(statement);
            List<Document> docs = paramBatches.stream().map(this::buildFilter).toList();
            col.insertMany(docs);
            return new int[]{docs.size()};
        }, VirtualExecutor.get());
    }

    @Contract("_ -> new")
    @Override
    public <T> @NotNull CompletableFuture<T> transactionAsync(TransactionCallback<T> callback) {
        return CompletableFuture.supplyAsync(() -> {
            try (ClientSession session = client.startSession()) {
                session.startTransaction();
                try {
                    T result = callback.execute(new MongoTransactionContext(db));
                    session.commitTransaction();
                    return result;
                } catch (Exception ex) {
                    session.abortTransaction();
                    throw new RuntimeException("Mongo transaction aborted", ex);
                }
            }
        }, VirtualExecutor.get());
    }

    @Override
    public boolean isConnected() {
        return client != null;
    }

    private @NotNull Document buildFilter(Object @NotNull [] params) {
        Document doc = new Document();
        for (int i = 0; i + 1 < params.length; i += 2) {
            doc.append(params[i].toString(), params[i + 1]);
        }
        return doc;
    }

    private record MongoTransactionContext(com.mongodb.client.MongoDatabase db) implements TransactionContext {
        @Override
        public @NotNull List<QueryResult> query(String collection, Object @NotNull ... params) {
            Document filter = new Document();
            for (int i = 0; i + 1 < params.length; i += 2) filter.append(params[i].toString(), params[i + 1]);
            List<QueryResult> out = new ArrayList<>();
            db.getCollection(collection).find(filter).forEach(d -> out.add(new QueryResult(d)));
            return out;
        }

        @Override
        public @NotNull UpdateResult update(String collection, Object @NotNull ... params) {
            Document doc = new Document();
            for (int i = 0; i + 1 < params.length; i += 2) doc.append(params[i].toString(), params[i + 1]);
            var res = db.getCollection(collection).insertOne(doc);
            return UpdateResult.of(1, res.getInsertedId());
        }
    }
}