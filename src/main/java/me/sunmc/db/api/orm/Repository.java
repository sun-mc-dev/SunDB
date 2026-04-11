package me.sunmc.db.api.orm;

import me.sunmc.db.api.AsyncDatabase;
import me.sunmc.db.api.query.InsertBuilder;
import me.sunmc.db.api.result.QueryResult;
import me.sunmc.db.util.ValueParser;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A reflection-based ORM repository that maps Java objects to database rows.
 * Only supports SQL databases via {@link AsyncDatabase}.
 *
 * <pre>{@code
 * @Table("users")
 * public class User {
 *     @Id @Column("uuid") public UUID id;
 *     @Column("name") public String name;
 * }
 *
 * Repository<User> repo = new Repository<>(db, User.class);
 * repo.save(myUser);
 * User u = repo.findById(uuid).orElse(null);
 * }</pre>
 */
public final class Repository<T> {

    private final AsyncDatabase database;
    private final Class<T> entityType;
    private final String tableName;

    private final List<FieldMapping> columns = new ArrayList<>();
    private FieldMapping idField;

    public Repository(@NotNull AsyncDatabase database, @NotNull Class<T> entityType) {
        this.database = database;
        this.entityType = entityType;

        Table tableAnn = entityType.getAnnotation(Table.class);
        if (tableAnn == null) {
            throw new IllegalArgumentException("Class must be annotated with @Table");
        }
        this.tableName = tableAnn.value();

        for (Field f : entityType.getDeclaredFields()) {
            Column colAnn = f.getAnnotation(Column.class);
            if (colAnn != null) {
                f.setAccessible(true);
                FieldMapping mapping = new FieldMapping(f, colAnn.value());
                columns.add(mapping);

                if (f.isAnnotationPresent(Id.class)) {
                    this.idField = mapping;
                }
            }
        }

        if (this.idField == null) {
            throw new IllegalArgumentException("Class must have one field annotated with @Id");
        }
    }

    /**
     * Saves the entity to the database synchronously.
     * Uses an upsert (INSERT ... ON DUPLICATE KEY UPDATE).
     */
    public void save(@NotNull T entity) {
        saveAsync(entity).join();
    }

    /**
     * Saves the entity to the database asynchronously.
     */
    public CompletableFuture<Void> saveAsync(@NotNull T entity) {
        try {
            InsertBuilder builder = database.insertInto(tableName);

            for (FieldMapping m : columns) {
                Object val = m.field.get(entity);
                if (val != null) {
                    builder.set(m.columnName, val.toString());
                    if (m != idField) { // Update everything except the PK on conflict
                        builder.onDuplicateKeyUpdate(m.columnName, val.toString());
                    }
                }
            }
            // Explicitly set the conflict target for Postgres/SQLite
            builder.conflictTarget(idField.columnName);

            return builder.executeAsync().thenRun(() -> {
            });
        } catch (IllegalAccessException e) {
            return CompletableFuture.failedFuture(new RuntimeException("ORM Reflection Error", e));
        }
    }

    /**
     * Finds an entity by its primary key ID.
     */
    public Optional<T> findById(@NotNull Object id) {
        return findByIdAsync(id).join();
    }

    public CompletableFuture<Optional<T>> findByIdAsync(@NotNull Object id) {
        return database.select(tableName)
                .where(idField.columnName, "=", id.toString())
                .limit(1)
                .executeAsync()
                .thenApply(rows -> {
                    if (rows.isEmpty()) return Optional.empty();
                    try {
                        return Optional.of(mapRow(rows.getFirst()));
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to map query result to " + entityType.getSimpleName(), e);
                    }
                });
    }

    private @NotNull T mapRow(QueryResult row) throws Exception {
        T instance = entityType.getDeclaredConstructor().newInstance();
        for (FieldMapping m : columns) {
            String raw = row.getString(m.columnName);
            if (raw != null) {
                Object val = ValueParser.parse(raw, m.field.getType());
                m.field.set(instance, val);
            }
        }
        return instance;
    }

    private record FieldMapping(Field field, String columnName) {
    }
}
