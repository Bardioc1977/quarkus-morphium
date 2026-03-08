package de.caluga.morphium.quarkus.data;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.query.Query;
import jakarta.data.Limit;
import jakarta.data.Order;
import jakarta.data.Sort;
import jakarta.data.page.PageRequest;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Runtime bridge for {@code @Query} annotated repository methods.
 * Parses the JDQL string (cached), resolves named parameters via the
 * {@code @Param} name-to-index mapping, and executes the query against Morphium.
 */
public final class JdqlMethodBridge {

    private static final ConcurrentHashMap<String, JdqlQuery> CACHE = new ConcurrentHashMap<>();

    private JdqlMethodBridge() {}

    /**
     * Executes a {@code @Query} annotated method.
     *
     * @param repo               the repository instance
     * @param jdql               the JDQL query string
     * @param paramMapSpec       encoded param name-to-index mapping: "cat:0,minPrice:1"
     * @param sortParamIndex     index of Sort parameter, -1 if absent
     * @param orderParamIndex    index of Order parameter, -1 if absent
     * @param pageRequestParamIndex index of PageRequest parameter, -1 if absent
     * @param limitParamIndex    index of Limit parameter, -1 if absent
     * @param args               the method arguments
     * @param returnsSingle      true if method returns a single entity
     * @param returnsCount       true if method returns long (count)
     * @param returnsBoolean     true if method returns boolean (exists)
     * @return the query result
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object executeJdql(AbstractMorphiumRepository<?, ?> repo,
                                     String jdql,
                                     String paramMapSpec,
                                     int sortParamIndex,
                                     int orderParamIndex,
                                     int pageRequestParamIndex,
                                     int limitParamIndex,
                                     Object[] args,
                                     boolean returnsSingle,
                                     boolean returnsCount,
                                     boolean returnsBoolean) {
        Morphium morphium = repo.getMorphium();
        Class entityClass = repo.getMetadata().entityClass();

        // Parse JDQL (cached)
        JdqlQuery query = CACHE.computeIfAbsent(jdql, JdqlParser::parse);

        // Build param name → value map
        Map<String, Object> paramValues = buildParamMap(paramMapSpec, args);

        // Build Morphium query
        Query mQuery = morphium.createQueryFor(entityClass);

        // Apply conditions
        applyConditions(mQuery, query, paramValues, morphium, entityClass);

        // Apply JDQL ORDER BY
        if (!query.orderBy().isEmpty()) {
            Map<String, Integer> sortMap = new LinkedHashMap<>();
            for (JdqlQuery.OrderSpec spec : query.orderBy()) {
                String mongoField = resolveMongoField(morphium, entityClass, spec.field());
                sortMap.put(mongoField, spec.ascending() ? 1 : -1);
            }
            mQuery.sort(sortMap);
        }

        // Apply dynamic Sort<T> parameter
        if (sortParamIndex >= 0 && args[sortParamIndex] != null) {
            Sort sort = (Sort) args[sortParamIndex];
            Map<String, Integer> sortMap = new LinkedHashMap<>();
            String mongoField = resolveMongoField(morphium, entityClass, sort.property());
            sortMap.put(mongoField, sort.isAscending() ? 1 : -1);
            mQuery.sort(sortMap);
        }

        // Apply dynamic Order<T> parameter
        if (orderParamIndex >= 0 && args[orderParamIndex] != null) {
            Order order = (Order) args[orderParamIndex];
            if (!order.sorts().isEmpty()) {
                Map<String, Integer> sortMap = new LinkedHashMap<>();
                for (Object s : order.sorts()) {
                    Sort sort = (Sort) s;
                    String mongoField = resolveMongoField(morphium, entityClass, sort.property());
                    sortMap.put(mongoField, sort.isAscending() ? 1 : -1);
                }
                mQuery.sort(sortMap);
            }
        }

        // Apply Limit
        if (limitParamIndex >= 0 && args[limitParamIndex] != null) {
            Limit limit = (Limit) args[limitParamIndex];
            mQuery.skip((int) (limit.startAt() - 1));
            mQuery.limit(limit.maxResults());
        }

        // Apply PageRequest → return Page<T>
        if (pageRequestParamIndex >= 0 && args[pageRequestParamIndex] != null) {
            PageRequest pageRequest = (PageRequest) args[pageRequestParamIndex];
            int size = pageRequest.size();
            long page = pageRequest.page();
            int skip = (int) ((page - 1) * size);
            mQuery.skip(skip).limit(size);

            List content = mQuery.asList();
            long totalElements = -1;
            if (pageRequest.requestTotal()) {
                Query countQuery = morphium.createQueryFor(entityClass);
                applyConditions(countQuery, query, paramValues, morphium, entityClass);
                totalElements = countQuery.countAll();
            }
            return new MorphiumPage<>(content, totalElements, pageRequest);
        }

        // Execute
        if (returnsCount) {
            return mQuery.countAll();
        }
        if (returnsBoolean) {
            return mQuery.countAll() > 0;
        }
        if (returnsSingle) {
            return mQuery.get();
        }
        return mQuery.asList();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void applyConditions(Query mQuery,
                                         JdqlQuery query,
                                         Map<String, Object> paramValues,
                                         Morphium morphium,
                                         Class entityClass) {
        boolean isOr = query.combinator() == JdqlQuery.Combinator.OR;

        if (isOr && query.conditions().size() > 1) {
            List<Query> orQueries = new ArrayList<>();
            for (JdqlQuery.JdqlCondition cond : query.conditions()) {
                Query sub = morphium.createQueryFor(entityClass);
                applyCondition(sub, cond, paramValues, morphium, entityClass);
                orQueries.add(sub);
            }
            mQuery.or(orQueries);
        } else {
            for (JdqlQuery.JdqlCondition cond : query.conditions()) {
                applyCondition(mQuery, cond, paramValues, morphium, entityClass);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void applyCondition(Query mQuery,
                                        JdqlQuery.JdqlCondition cond,
                                        Map<String, Object> paramValues,
                                        Morphium morphium,
                                        Class entityClass) {
        String mongoField = resolveMongoField(morphium, entityClass, cond.fieldName());
        var field = mQuery.f(mongoField);

        Object value = resolveValue(cond.valueRef(), paramValues);

        switch (cond.operator()) {
            case EQ -> {
                if (cond.literal() != null) {
                    field.eq(cond.literal());
                } else {
                    field.eq(value);
                }
            }
            case NE -> {
                if (cond.literal() != null) {
                    field.ne(cond.literal());
                } else {
                    field.ne(value);
                }
            }
            case GT -> field.gt(value);
            case GTE -> field.gte(value);
            case LT -> field.lt(value);
            case LTE -> field.lte(value);
            case BETWEEN -> {
                Object value2 = resolveValue(cond.valueRef2(), paramValues);
                field.gte(value);
                mQuery.f(mongoField).lte(value2);
            }
            case IN -> field.in((Collection) value);
            case NOT_IN -> field.nin((Collection) value);
            case LIKE -> {
                String pattern = value.toString()
                        .replace("%", ".*")
                        .replace("_", ".");
                field.matches(Pattern.compile(pattern));
            }
            case IS_NULL -> field.eq(null);
            case IS_NOT_NULL -> field.ne(null);
        }
    }

    private static Object resolveValue(String valueRef, Map<String, Object> paramValues) {
        if (valueRef == null) return null;
        if (valueRef.startsWith(":")) {
            String paramName = valueRef.substring(1);
            if (!paramValues.containsKey(paramName)) {
                throw new IllegalArgumentException(
                        "JDQL parameter :" + paramName + " not found. Available: " + paramValues.keySet());
            }
            return paramValues.get(paramName);
        }
        // Try numeric literal
        try {
            if (valueRef.contains(".")) {
                return Double.parseDouble(valueRef);
            }
            return Long.parseLong(valueRef);
        } catch (NumberFormatException e) {
            // Return as string literal (strip quotes if present)
            if ((valueRef.startsWith("'") && valueRef.endsWith("'"))
                    || (valueRef.startsWith("\"") && valueRef.endsWith("\""))) {
                return valueRef.substring(1, valueRef.length() - 1);
            }
            return valueRef;
        }
    }

    private static Map<String, Object> buildParamMap(String paramMapSpec, Object[] args) {
        Map<String, Object> map = new HashMap<>();
        if (paramMapSpec == null || paramMapSpec.isEmpty()) return map;
        for (String entry : paramMapSpec.split(",")) {
            String[] parts = entry.split(":");
            String name = parts[0];
            int idx = Integer.parseInt(parts[1]);
            map.put(name, args[idx]);
        }
        return map;
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
