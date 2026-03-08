package de.caluga.morphium.quarkus.data;

import java.util.List;

/**
 * Parsed representation of a JDQL (Jakarta Data Query Language) query.
 * Created by {@link JdqlParser} from a {@code @Query} annotation value.
 */
public record JdqlQuery(
        List<JdqlCondition> conditions,
        Combinator combinator,
        List<OrderSpec> orderBy
) {

    public enum Combinator { AND, OR }

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
     */
    public record JdqlCondition(
            String fieldName,
            Operator operator,
            String valueRef,
            String valueRef2,
            Object literal
    ) {}

    public record OrderSpec(String field, boolean ascending) {}
}
