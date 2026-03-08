package de.caluga.morphium.quarkus.data;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.query.Query;
import jakarta.data.Limit;
import jakarta.data.Order;
import jakarta.data.Sort;
import jakarta.data.page.Page;
import jakarta.data.page.PageRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Runtime bridge called by Gizmo-generated repository methods for
 * {@code @Find}, {@code @Delete} annotated methods with {@code @By} parameters.
 * <p>
 * The build-time processor encodes the query specification as simple strings
 * (field:paramIndex pairs and orderBy specs) to avoid complex Gizmo bytecode.
 */
public final class FindMethodBridge {

    private FindMethodBridge() {}

    /**
     * Executes a {@code @Find} annotated method.
     *
     * @param repo               the repository instance
     * @param conditionsSpec     encoded conditions: "field1:0,field2:1" (fieldName:paramIndex, all EQ)
     * @param orderBySpec        encoded ordering: "field1:ASC,field2:DESC" or "" for none
     * @param sortParamIndex     index of Sort parameter, -1 if absent
     * @param orderParamIndex    index of Order parameter, -1 if absent
     * @param pageRequestParamIndex index of PageRequest parameter, -1 if absent
     * @param limitParamIndex    index of Limit parameter, -1 if absent
     * @param args               the method arguments
     * @param returnsSingle      true if method returns a single entity (not List/Stream/Page)
     * @return the query result
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object executeFind(AbstractMorphiumRepository<?, ?> repo,
                                     String conditionsSpec,
                                     String orderBySpec,
                                     int sortParamIndex,
                                     int orderParamIndex,
                                     int pageRequestParamIndex,
                                     int limitParamIndex,
                                     Object[] args,
                                     boolean returnsSingle) {
        Morphium morphium = repo.getMorphium();
        Class entityClass = repo.getMetadata().entityClass();
        Query query = morphium.createQueryFor(entityClass);

        // Apply equality conditions from @By parameters
        if (!conditionsSpec.isEmpty()) {
            for (String part : conditionsSpec.split(",")) {
                String[] fieldAndIdx = part.split(":");
                String javaField = fieldAndIdx[0];
                int paramIdx = Integer.parseInt(fieldAndIdx[1]);
                String mongoField = resolveMongoField(morphium, entityClass, javaField);
                query.f(mongoField).eq(args[paramIdx]);
            }
        }

        // Apply static @OrderBy sorting
        if (!orderBySpec.isEmpty()) {
            Map<String, Integer> sortMap = new LinkedHashMap<>();
            for (String part : orderBySpec.split(",")) {
                String[] fieldAndDir = part.split(":");
                String javaField = fieldAndDir[0];
                String dir = fieldAndDir[1];
                String mongoField = resolveMongoField(morphium, entityClass, javaField);
                sortMap.put(mongoField, "DESC".equals(dir) ? -1 : 1);
            }
            query.sort(sortMap);
        }

        // Apply dynamic Sort<T> parameter
        if (sortParamIndex >= 0 && args[sortParamIndex] != null) {
            Sort sort = (Sort) args[sortParamIndex];
            Map<String, Integer> sortMap = new LinkedHashMap<>();
            String mongoField = resolveMongoField(morphium, entityClass, sort.property());
            sortMap.put(mongoField, sort.isAscending() ? 1 : -1);
            query.sort(sortMap);
        }

        // Apply dynamic Order<T> parameter (contains multiple Sort entries)
        if (orderParamIndex >= 0 && args[orderParamIndex] != null) {
            Order order = (Order) args[orderParamIndex];
            if (!order.sorts().isEmpty()) {
                Map<String, Integer> sortMap = new LinkedHashMap<>();
                for (Object s : order.sorts()) {
                    Sort sort = (Sort) s;
                    String mongoField = resolveMongoField(morphium, entityClass, sort.property());
                    sortMap.put(mongoField, sort.isAscending() ? 1 : -1);
                }
                query.sort(sortMap);
            }
        }

        // Apply Limit parameter
        if (limitParamIndex >= 0 && args[limitParamIndex] != null) {
            Limit limit = (Limit) args[limitParamIndex];
            query.skip((int) (limit.startAt() - 1));
            query.limit(limit.maxResults());
        }

        // Apply PageRequest parameter → return Page<T>
        if (pageRequestParamIndex >= 0 && args[pageRequestParamIndex] != null) {
            PageRequest pageRequest = (PageRequest) args[pageRequestParamIndex];
            int size = pageRequest.size();
            long page = pageRequest.page();
            int skip = (int) ((page - 1) * size);
            query.skip(skip).limit(size);

            List content = query.asList();
            long totalElements = -1;
            if (pageRequest.requestTotal()) {
                // Re-create query for total count (without skip/limit)
                Query countQuery = morphium.createQueryFor(entityClass);
                if (!conditionsSpec.isEmpty()) {
                    for (String p : conditionsSpec.split(",")) {
                        String[] fieldAndIdx = p.split(":");
                        String mongoField = resolveMongoField(morphium, entityClass, fieldAndIdx[0]);
                        countQuery.f(mongoField).eq(args[Integer.parseInt(fieldAndIdx[1])]);
                    }
                }
                totalElements = countQuery.countAll();
            }
            return new MorphiumPage<>(content, totalElements, pageRequest);
        }

        // Execute
        if (returnsSingle) {
            return query.get();
        }
        return query.asList();
    }

    /**
     * Executes a {@code @Delete} annotated method with {@code @By} parameters.
     *
     * @param repo           the repository instance
     * @param conditionsSpec encoded conditions (same format as executeFind)
     * @param args           the method arguments
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void executeAnnotatedDelete(AbstractMorphiumRepository<?, ?> repo,
                                              String conditionsSpec,
                                              Object[] args) {
        Morphium morphium = repo.getMorphium();
        Class entityClass = repo.getMetadata().entityClass();
        Query query = morphium.createQueryFor(entityClass);

        if (!conditionsSpec.isEmpty()) {
            for (String part : conditionsSpec.split(",")) {
                String[] fieldAndIdx = part.split(":");
                String javaField = fieldAndIdx[0];
                int paramIdx = Integer.parseInt(fieldAndIdx[1]);
                String mongoField = resolveMongoField(morphium, entityClass, javaField);
                query.f(mongoField).eq(args[paramIdx]);
            }
        }

        List toDelete = query.asList();
        for (Object entity : toDelete) {
            morphium.delete(entity);
        }
    }

    @SuppressWarnings("unchecked")
    private static String resolveMongoField(Morphium morphium, Class entityClass, String javaFieldName) {
        try {
            return morphium.getARHelper().getMongoFieldName(entityClass, javaFieldName);
        } catch (Exception e) {
            return javaFieldName;
        }
    }
}
