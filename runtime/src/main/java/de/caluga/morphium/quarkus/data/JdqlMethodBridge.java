package de.caluga.morphium.quarkus.data;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.aggregation.Aggregator;
import de.caluga.morphium.aggregation.Group;
import de.caluga.morphium.query.Query;
import jakarta.data.Limit;
import jakarta.data.Order;
import jakarta.data.Sort;
import jakarta.data.page.PageRequest;
import jakarta.data.page.impl.CursoredPageRecord;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
     * @param returnsSingle      true if method returns a single entity T
     * @param returnsCount       true if method returns long (count)
     * @param returnsBoolean     true if method returns boolean (exists)
     * @param returnsOptional    true if method returns Optional&lt;T&gt;
     * @param returnsCursoredPage true if method returns CursoredPage&lt;T&gt;
     * @param orderBySpec        encoded ordering from {@code @OrderBy}: "field1:ASC,field2:DESC"
     * @param returnsStream      true if method returns Stream&lt;T&gt;
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
                                     boolean returnsBoolean,
                                     boolean returnsOptional,
                                     boolean returnsCursoredPage,
                                     String orderBySpec,
                                     boolean returnsStream) {
        Morphium morphium = repo.getMorphium();
        Class entityClass = repo.getMetadata().entityClass();

        // Parse JDQL (cached)
        JdqlQuery query = CACHE.computeIfAbsent(jdql, JdqlParser::parse);

        // Build param name → value map
        Map<String, Object> paramValues = buildParamMap(paramMapSpec, args);

        // Aggregate functions → Aggregation Pipeline (early return)
        if (query.aggregateFunctions() != null && !query.aggregateFunctions().isEmpty()) {
            return executeAggregate(repo, query, paramValues, morphium, entityClass);
        }

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

        // Apply SELECT projection (skip for COUNT/EXISTS — they don't need it)
        if (query.selectFields() != null && !query.selectFields().isEmpty()
                && !returnsCount && !returnsBoolean) {
            for (String field : query.selectFields()) {
                String mongoField = resolveMongoField(morphium, entityClass, field);
                mQuery.addProjection(mongoField);
            }
        }

        // Apply PageRequest → return Page<T> or CursoredPage<T>
        if (pageRequestParamIndex >= 0 && args[pageRequestParamIndex] != null) {
            PageRequest pageRequest = (PageRequest) args[pageRequestParamIndex];

            if (returnsCursoredPage) {
                return executeCursoredJdql(mQuery, pageRequest, orderBySpec, query, paramValues,
                        morphium, entityClass);
            }

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
        if (returnsOptional) {
            return QueryResultHelper.optionalSingle(mQuery);
        }
        if (returnsSingle) {
            return QueryResultHelper.requireSingle(mQuery);
        }
        if (returnsStream) {
            return mQuery.stream();
        }
        return mQuery.asList();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object executeCursoredJdql(Query mQuery, PageRequest pageRequest,
                                               String orderBySpec, JdqlQuery jdqlQuery,
                                               Map<String, Object> paramValues,
                                               Morphium morphium, Class entityClass) {
        List<CursorHelper.SortSpec> sortSpecs = CursorHelper.parseSortSpecs(orderBySpec);
        boolean isForward = pageRequest.mode() != PageRequest.Mode.CURSOR_PREVIOUS;

        if (pageRequest.mode() != PageRequest.Mode.OFFSET) {
            PageRequest.Cursor cursor = pageRequest.cursor()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "PageRequest mode is " + pageRequest.mode() + " but no cursor provided"));
            CursorHelper.applyCursorCondition(mQuery, cursor, sortSpecs, morphium, entityClass, isForward);
        }

        CursorHelper.applySort(mQuery, sortSpecs, morphium, entityClass, isForward);
        int requestedSize = pageRequest.size();
        mQuery.limit(requestedSize + 1);

        List content = mQuery.asList();
        boolean hasMore = content.size() > requestedSize;
        if (hasMore) {
            content = new ArrayList(content.subList(0, requestedSize));
        }
        if (!isForward) {
            Collections.reverse(content);
        }

        List<String> sortFields = sortSpecs.stream().map(CursorHelper.SortSpec::javaField).toList();
        List<PageRequest.Cursor> cursors = CursorHelper.extractCursors(content, sortFields, morphium, entityClass);

        long totalElements = -1;
        if (pageRequest.requestTotal()) {
            Query countQuery = morphium.createQueryFor(entityClass);
            applyConditions(countQuery, jdqlQuery, paramValues, morphium, entityClass);
            totalElements = countQuery.countAll();
        }

        boolean isFirstPage = pageRequest.mode() == PageRequest.Mode.OFFSET;
        boolean isLastPage = !hasMore;

        if (content.isEmpty()) {
            return new CursoredPageRecord<>(content, cursors, totalElements, pageRequest,
                    (PageRequest) null, (PageRequest) null);
        }

        return new CursoredPageRecord<>(content, cursors, totalElements, pageRequest,
                isFirstPage, isLastPage);
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object executeAggregate(AbstractMorphiumRepository<?, ?> repo,
                                            JdqlQuery query,
                                            Map<String, Object> paramValues,
                                            Morphium morphium, Class entityClass) {
        Aggregator agg = morphium.createAggregator(entityClass, Map.class);

        // $match stage: WHERE conditions
        if (!query.conditions().isEmpty()) {
            Query matchQuery = morphium.createQueryFor(entityClass);
            applyConditions(matchQuery, query, paramValues, morphium, entityClass);
            agg.match(matchQuery);
        }

        // $group stage: _id=null (global aggregation)
        Group group = agg.group((String) null);
        for (int i = 0; i < query.aggregateFunctions().size(); i++) {
            JdqlQuery.AggregateFunction func = query.aggregateFunctions().get(i);
            String resultField = "agg_" + i;
            String mongoField = "this".equals(func.field()) ? null
                    : "$" + resolveMongoField(morphium, entityClass, func.field());

            switch (func.type()) {
                case COUNT -> group.sum(resultField, 1);
                case SUM -> group.sum(resultField, mongoField);
                case AVG -> group.avg(resultField, mongoField);
                case MIN -> group.min(resultField, mongoField);
                case MAX -> group.max(resultField, mongoField);
            }
        }
        group.end();

        List<Map<String, Object>> results = agg.aggregateMap();

        // Empty result → default values
        if (results.isEmpty()) {
            if (query.aggregateFunctions().size() == 1) {
                return defaultAggregateValue(query.aggregateFunctions().get(0).type());
            }
            Object[] arr = new Object[query.aggregateFunctions().size()];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = defaultAggregateValue(query.aggregateFunctions().get(i).type());
            }
            return arr;
        }

        Map<String, Object> result = results.get(0);

        if (query.aggregateFunctions().size() == 1) {
            return toNumber(result.get("agg_0"), query.aggregateFunctions().get(0).type());
        }

        // Multiple aggregates → Object[]
        Object[] arr = new Object[query.aggregateFunctions().size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = toNumber(result.get("agg_" + i), query.aggregateFunctions().get(i).type());
        }
        return arr;
    }

    private static Object defaultAggregateValue(JdqlQuery.AggregateType type) {
        return switch (type) {
            case COUNT -> 0L;
            case SUM, AVG, MIN, MAX -> 0.0;
        };
    }

    private static Object toNumber(Object value, JdqlQuery.AggregateType type) {
        if (value == null) return defaultAggregateValue(type);
        if (type == JdqlQuery.AggregateType.COUNT) {
            return ((Number) value).longValue();
        }
        if (value instanceof Number n) {
            return (n instanceof Integer || n instanceof Long) ? n.longValue() : n.doubleValue();
        }
        return value;
    }

    public static CompletionStage<Object> executeJdqlAsync(AbstractMorphiumRepository<?, ?> repo,
                                                              String jdql,
                                                              String paramMapSpec,
                                                              int sortParamIndex,
                                                              int orderParamIndex,
                                                              int pageRequestParamIndex,
                                                              int limitParamIndex,
                                                              Object[] args,
                                                              boolean returnsSingle,
                                                              boolean returnsCount,
                                                              boolean returnsBoolean,
                                                              boolean returnsOptional,
                                                              boolean returnsCursoredPage,
                                                              String orderBySpec,
                                                              boolean returnsStream) {
        return CompletableFuture.supplyAsync(
                () -> executeJdql(repo, jdql, paramMapSpec, sortParamIndex, orderParamIndex,
                        pageRequestParamIndex, limitParamIndex, args, returnsSingle, returnsCount,
                        returnsBoolean, returnsOptional, returnsCursoredPage, orderBySpec, returnsStream),
                repo.getAsyncExecutor());
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
