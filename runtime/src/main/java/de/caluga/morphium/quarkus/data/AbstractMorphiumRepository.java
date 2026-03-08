package de.caluga.morphium.quarkus.data;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.query.Query;
import jakarta.data.Order;
import jakarta.data.Sort;
import jakarta.data.page.Page;
import jakarta.data.page.PageRequest;
import jakarta.inject.Inject;

import java.util.*;
import java.util.stream.Stream;

/**
 * Base class for Gizmo-generated repository implementations.
 * Contains all CRUD logic as regular Java methods; the generated subclass
 * only provides the thin bridge from the repository interface methods to
 * the {@code doXxx()} methods here.
 *
 * @param <T> the entity type
 * @param <K> the primary-key type
 */
public abstract class AbstractMorphiumRepository<T, K> {

    @Inject
    Morphium morphium;

    private final RepositoryMetadata metadata;

    public AbstractMorphiumRepository(RepositoryMetadata metadata) {
        this.metadata = metadata;
    }

    // -- accessors for subclasses and QueryExecutor --------------------------

    public Morphium getMorphium() {
        return morphium;
    }

    public RepositoryMetadata getMetadata() {
        return metadata;
    }

    @SuppressWarnings("unchecked")
    public Class<T> entityClass() {
        return (Class<T>) metadata.entityClass();
    }

    // -- BasicRepository CRUD operations -------------------------------------

    @SuppressWarnings("unchecked")
    public Optional<T> doFindById(K id) {
        T result = (T) morphium.findById(entityClass(), id, null);
        return Optional.ofNullable(result);
    }

    public Stream<T> doFindAll() {
        return morphium.readAll(entityClass()).stream();
    }

    @SuppressWarnings("unchecked")
    public Page<T> doFindAllPaged(PageRequest pageRequest, Order<T> sortBy) {
        Query<T> query = morphium.createQueryFor(entityClass());

        // Apply sorting from Order
        if (sortBy != null && !sortBy.sorts().isEmpty()) {
            Map<String, Integer> sortMap = new LinkedHashMap<>();
            for (Sort<? super T> sort : sortBy.sorts()) {
                String mongoField = resolveMongoField(sort.property());
                sortMap.put(mongoField, sort.isAscending() ? 1 : -1);
            }
            query.sort(sortMap);
        }

        // Apply pagination
        int size = pageRequest.size();
        long page = pageRequest.page();
        int skip = (int) ((page - 1) * size);
        query.skip(skip).limit(size);

        List<T> content = query.asList();

        // Total count (only if requested)
        long totalElements = -1;
        if (pageRequest.requestTotal()) {
            totalElements = morphium.createQueryFor(entityClass()).countAll();
        }

        return new MorphiumPage<>(content, totalElements, pageRequest);
    }

    public Object doSave(Object entity) {
        morphium.store(entity);
        return entity;
    }

    @SuppressWarnings("unchecked")
    public List<Object> doSaveAll(List<?> entities) {
        morphium.storeList((List) entities);
        return (List<Object>) entities;
    }

    public Object doInsert(Object entity) {
        morphium.insert(entity);
        return entity;
    }

    @SuppressWarnings("unchecked")
    public List<Object> doInsertAll(List<?> entities) {
        morphium.insertList((List) entities);
        return (List<Object>) entities;
    }

    public Object doUpdate(Object entity) {
        morphium.store(entity);
        return entity;
    }

    @SuppressWarnings("unchecked")
    public List<Object> doUpdateAll(List<?> entities) {
        morphium.storeList((List) entities);
        return (List<Object>) entities;
    }

    public void doDelete(Object entity) {
        morphium.delete(entity);
    }

    @SuppressWarnings("unchecked")
    public void doDeleteById(K id) {
        T entity = (T) morphium.findById(entityClass(), id, null);
        if (entity != null) {
            morphium.delete(entity);
        }
    }

    @SuppressWarnings("unchecked")
    public void doDeleteAll(List<?> entities) {
        for (Object entity : entities) {
            morphium.delete(entity);
        }
    }

    public Query<T> createQuery() {
        return morphium.createQueryFor(entityClass());
    }

    @SuppressWarnings("unchecked")
    private String resolveMongoField(String javaFieldName) {
        try {
            return morphium.getARHelper().getMongoFieldName(entityClass(), javaFieldName);
        } catch (Exception e) {
            return javaFieldName;
        }
    }
}
