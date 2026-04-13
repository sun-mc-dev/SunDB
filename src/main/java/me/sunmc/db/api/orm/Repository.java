package me.sunmc.db.api.orm;

import me.sunmc.db.api.AsyncDatabase;
import me.sunmc.db.api.query.InsertBuilder;
import me.sunmc.db.api.result.QueryResult;
import me.sunmc.db.util.SqlSanitizer;
import me.sunmc.db.util.ValueParser;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private static final Map<Class<?>, Repository<?>> repoCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final AsyncDatabase database;
    private final Class<T> entityType;
    private final String tableName;
    private final List<FieldMapping> columns = new ArrayList<>();
    private final List<RelationshipMapping> relationships = new ArrayList<>();
    private FieldMapping idField;

    public Repository(@NotNull AsyncDatabase database, @NotNull Class<T> entityType) {
        this.database = database;
        this.entityType = entityType;

        Table tableAnn = entityType.getAnnotation(Table.class);
        if (tableAnn == null) {
            throw new IllegalArgumentException("Class must be annotated with @Table");
        }
        this.tableName = SqlSanitizer.table(tableAnn.value());

        for (Field f : entityType.getDeclaredFields()) {
            f.setAccessible(true);
            Column colAnn = f.getAnnotation(Column.class);
            if (colAnn != null) {
                FieldMapping mapping = new FieldMapping(f, SqlSanitizer.column(colAnn.value()));
                columns.add(mapping);

                if (f.isAnnotationPresent(Id.class)) {
                    this.idField = mapping;
                }
            } else if (f.isAnnotationPresent(ManyToOne.class)) {
                relationships.add(new RelationshipMapping(f, RelationshipType.MANY_TO_ONE, null));
            } else if (f.isAnnotationPresent(OneToMany.class)) {
                OneToMany ann = f.getAnnotation(OneToMany.class);
                relationships.add(new RelationshipMapping(f, RelationshipType.ONE_TO_MANY, ann.mappedBy()));
            } else if (f.isAnnotationPresent(OneToOne.class)) {
                OneToOne ann = f.getAnnotation(OneToOne.class);
                relationships.add(new RelationshipMapping(f, RelationshipType.ONE_TO_ONE, ann.mappedBy()));
            }
        }

        if (this.idField == null) {
            throw new IllegalArgumentException("Class must have one field annotated with @Id");
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> Repository<T> of(@NotNull AsyncDatabase db, @NotNull Class<T> type) {
        return (Repository<T>) repoCache.computeIfAbsent(type, t -> new Repository<>(db, t));
    }

    /**
     * Purges the repository cache. Should be called during database reloads.
     */
    public static void clearCache() {
        repoCache.clear();
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

        // Handle Relationships
        for (RelationshipMapping rel : relationships) {
            if (rel.type == RelationshipType.MANY_TO_ONE) {
                Object relId = row.raw().get(rel.field.getName() + "_id");
                if (relId != null) {
                    Repository<?> relRepo = Repository.of(database, rel.field.getType());
                    rel.field.set(instance, relRepo.findById(relId).orElse(null));
                }
            } else if (rel.type == RelationshipType.ONE_TO_MANY) {
                // Return a simple lazy-loading list
                Class<?> targetType = (Class<?>) ((ParameterizedType) rel.field.getGenericType()).getActualTypeArguments()[0];
                Repository<?> relRepo = Repository.of(database, targetType);

                Object myId = idField.field.get(instance);
                rel.field.set(instance, new LazyList<>(() ->
                        database.select(relRepo.tableName)
                                .where(rel.mappedBy, "=", myId.toString())
                                .executeAsync().thenApply(targetRows -> {
                                    List<Object> list = new ArrayList<>();
                                    for (QueryResult r : targetRows) {
                                        try {
                                            list.add(relRepo.mapRow(r));
                                        } catch (Exception ignored) {
                                        }
                                    }
                                    return list;
                                }).join()
                ));
            } else if (rel.type == RelationshipType.ONE_TO_ONE) {
                Repository<?> relRepo = Repository.of(database, rel.field.getType());
                if (rel.mappedBy == null || rel.mappedBy.isEmpty()) {
                    // Owning Side
                    Object relId = row.raw().get(rel.field.getName() + "_id");
                    if (relId != null) {
                        rel.field.set(instance, relRepo.findById(relId).orElse(null));
                    }
                } else {
                    // Inverse Side
                    Object myId = idField.field.get(instance);
                    rel.field.set(instance, database.select(relRepo.tableName)
                            .where(rel.mappedBy, "=", myId.toString())
                            .executeAsync().thenApply(targetRows -> {
                                if (targetRows.isEmpty()) return null;
                                try {
                                    return relRepo.mapRow(targetRows.getFirst());
                                } catch (Exception e) {
                                    return null;
                                }
                            }).join());
                }
            }
        }
        return instance;
    }

    private enum RelationshipType {MANY_TO_ONE, ONE_TO_MANY, ONE_TO_ONE}

    private record FieldMapping(Field field, String columnName) {
    }

    private record RelationshipMapping(Field field, RelationshipType type, String mappedBy) {
    }
}
