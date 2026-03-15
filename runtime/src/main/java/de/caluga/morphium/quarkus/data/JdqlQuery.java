package de.caluga.morphium.quarkus.data;

import java.util.List;

/**
 * Parsed representation of a JDQL (Jakarta Data Query Language) query.
 * Created by {@link JdqlParser} from a {@code @Query} annotation value.
 *
 * @param selectFields projected field names from SELECT clause, null or empty = all fields
 * @param groupByFields fields from GROUP BY clause, null when no GROUP BY
 */
public record JdqlQuery(
        List<String> selectFields,
        List<AggregateFunction> aggregateFunctions,
        List<JdqlCondition> conditions,
        Combinator combinator,
        List<OrderSpec> orderBy,
        List<String> groupByFields,
        List<HavingCondition> havingConditions,
        Combinator havingCombinator
) {

    public enum Combinator { AND, OR }

    public enum AggregateType { COUNT, SUM, AVG, MIN, MAX }

    public record AggregateFunction(AggregateType type, String field) {}

    public enum Operator {
        EQ, NE, GT, GTE, LT, LTE,
        BETWEEN, IN, NOT_IN,
        LIKE, IS_NULL, IS_NOT_NULL
    }

    /**
     * A single JDQL condition.
     *
     * @param fieldName   the entity field name
     * @param operator    the comparison operator
     * @param valueRef    parameter reference (":name") or literal value, null for IS NULL/IS NOT NULL
     * @param valueRef2   second param/literal for BETWEEN, null otherwise
     * @param literal     literal value (Boolean, etc.) when not using a parameter reference
     * @param negated     true if the condition is prefixed with NOT
     */
    public record JdqlCondition(
            String fieldName,
            Operator operator,
            String valueRef,
            String valueRef2,
            Object literal,
            boolean negated
    ) {
        /** Convenience constructor without negation (backwards-compatible). */
        public JdqlCondition(String fieldName, Operator operator, String valueRef,
                             String valueRef2, Object literal) {
            this(fieldName, operator, valueRef, valueRef2, literal, false);
        }
    }

    public record OrderSpec(String field, boolean ascending) {}

    /**
     * A single HAVING condition referencing an aggregate result.
     *
     * @param aggregateFunction canonical form, e.g. "COUNT(this)" or "SUM(amount)"
     * @param operator          comparison operator
     * @param valueRef          parameter reference (":name") or numeric literal string
     */
    public record HavingCondition(
            String aggregateFunction,
            Operator operator,
            String valueRef
    ) {}
}
