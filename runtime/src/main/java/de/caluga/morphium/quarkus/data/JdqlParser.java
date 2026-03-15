package de.caluga.morphium.quarkus.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a JDQL (Jakarta Data Query Language) string into a {@link JdqlQuery}.
 * <p>
 * Supported JDQL subset (MongoDB-compatible):
 * <pre>
 * [SELECT field1, field2 [FROM EntityName]] [WHERE condition [AND|OR condition ...]] [ORDER BY field [ASC|DESC] [, ...]]
 * </pre>
 * Conditions:
 * <ul>
 *   <li>{@code field = :param} / {@code field <> :param} / {@code field != :param}</li>
 *   <li>{@code field > :param} / {@code field >= :param} / {@code field < :param} / {@code field <= :param}</li>
 *   <li>{@code field BETWEEN :min AND :max}</li>
 *   <li>{@code field IN :param}</li>
 *   <li>{@code field NOT IN :param}</li>
 *   <li>{@code field LIKE :param}</li>
 *   <li>{@code field IS NULL} / {@code field IS NOT NULL}</li>
 *   <li>Boolean literals: {@code field = true} / {@code field = false}</li>
 *   <li>Numeric literals: {@code field > 100}</li>
 * </ul>
 * Not supported: JOINs, GROUP BY, HAVING, subqueries.
 */
public final class JdqlParser {

    private JdqlParser() {}

    // Split ORDER BY from WHERE (case-insensitive)
    private static final Pattern ORDER_BY_SPLIT = Pattern.compile(
            "^(.+?)\\s+ORDER\\s+BY\\s+(.+)$", Pattern.CASE_INSENSITIVE);

    // Match aggregate function: COUNT(this), SUM(amount), AVG(field), MIN(field), MAX(field)
    private static final Pattern AGGREGATE_PATTERN = Pattern.compile(
            "(?i)(COUNT|SUM|AVG|MIN|MAX)\\s*\\(\\s*([a-zA-Z_][a-zA-Z0-9_.]*|this)\\s*\\)");

    // Match a named parameter :paramName
    private static final Pattern PARAM_REF = Pattern.compile(":([a-zA-Z_][a-zA-Z0-9_]*)");

    // Match numeric literal (int or double)
    private static final Pattern NUMERIC_LITERAL = Pattern.compile("-?\\d+(\\.\\d+)?");

    /**
     * Parses a JDQL query string.
     *
     * @param jdql the JDQL string (may or may not start with "SELECT" or "WHERE")
     * @return the parsed query descriptor
     * @throws IllegalArgumentException if the JDQL cannot be parsed
     */
    public static JdqlQuery parse(String jdql) {
        if (jdql == null || jdql.isBlank()) {
            return new JdqlQuery(null, null, List.of(), JdqlQuery.Combinator.AND, List.of());
        }

        String trimmed = jdql.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);

        // --- Parse SELECT clause ---
        List<String> selectFields = null;
        List<JdqlQuery.AggregateFunction> aggregateFunctions = null;
        if (upper.startsWith("SELECT ")) {
            int selectEnd = findSelectEnd(upper);
            String selectPart = trimmed.substring("SELECT ".length(), selectEnd).trim();
            List<String> rawFields = Arrays.stream(selectPart.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();

            // Classify each field: aggregate function or plain field
            List<String> plainFields = new ArrayList<>();
            List<JdqlQuery.AggregateFunction> aggFuncs = new ArrayList<>();
            for (String field : rawFields) {
                Matcher aggMatcher = AGGREGATE_PATTERN.matcher(field);
                if (aggMatcher.matches()) {
                    String funcName = aggMatcher.group(1).toUpperCase(Locale.ROOT);
                    String argName = aggMatcher.group(2);
                    JdqlQuery.AggregateType type = JdqlQuery.AggregateType.valueOf(funcName);
                    aggFuncs.add(new JdqlQuery.AggregateFunction(type, argName));
                } else {
                    plainFields.add(field);
                }
            }

            if (!aggFuncs.isEmpty() && !plainFields.isEmpty()) {
                throw new IllegalArgumentException(
                        "Mixing aggregate functions and field projections requires GROUP BY (not supported in v1): " + selectPart);
            }

            if (!aggFuncs.isEmpty()) {
                aggregateFunctions = aggFuncs;
            } else {
                selectFields = plainFields;
            }

            // Advance past SELECT fields
            trimmed = trimmed.substring(selectEnd).trim();
            upper = trimmed.toUpperCase(Locale.ROOT);

            // Skip optional FROM clause
            if (upper.startsWith("FROM ")) {
                int fromEnd = findFromEnd(upper);
                trimmed = trimmed.substring(fromEnd).trim();
                upper = trimmed.toUpperCase(Locale.ROOT);
            }
        }

        // --- From here, the remainder is [WHERE ...] [ORDER BY ...] ---
        if (trimmed.isEmpty()) {
            return new JdqlQuery(selectFields, aggregateFunctions, List.of(), JdqlQuery.Combinator.AND, List.of());
        }

        // Split off ORDER BY
        List<JdqlQuery.OrderSpec> orderBy = new ArrayList<>();
        String wherePart = trimmed;

        Matcher orderMatcher = ORDER_BY_SPLIT.matcher(trimmed);
        if (orderMatcher.matches()) {
            wherePart = orderMatcher.group(1).trim();
            String orderPart = orderMatcher.group(2).trim();
            orderBy = parseOrderBy(orderPart);
        }

        // Strip leading WHERE keyword
        if (wherePart.toUpperCase(Locale.ROOT).startsWith("WHERE ")) {
            wherePart = wherePart.substring(6).trim();
        }

        // If empty after stripping WHERE, no conditions
        if (wherePart.isEmpty()) {
            return new JdqlQuery(selectFields, aggregateFunctions, List.of(), JdqlQuery.Combinator.AND, orderBy);
        }

        // Determine combinator and split conditions
        JdqlQuery.Combinator combinator = JdqlQuery.Combinator.AND;
        List<String> conditionStrings;

        // Check for OR (case-insensitive, not inside BETWEEN...AND or ORDER BY)
        if (containsTopLevelOr(wherePart)) {
            combinator = JdqlQuery.Combinator.OR;
            conditionStrings = splitTopLevel(wherePart, "OR");
        } else {
            conditionStrings = splitTopLevel(wherePart, "AND");
        }

        List<JdqlQuery.JdqlCondition> conditions = new ArrayList<>();
        for (String condStr : conditionStrings) {
            conditions.add(parseCondition(condStr.trim()));
        }

        return new JdqlQuery(selectFields, aggregateFunctions, conditions, combinator, orderBy);
    }

    // -- SELECT clause helpers --

    /**
     * Finds the end index of the SELECT field list.
     * The SELECT clause ends at the first FROM, WHERE, or ORDER BY keyword.
     */
    private static int findSelectEnd(String upper) {
        int fromIdx = indexOfKeyword(upper, " FROM ");
        int whereIdx = indexOfKeyword(upper, " WHERE ");
        int orderByIdx = indexOfKeyword(upper, " ORDER BY ");
        int end = smallestPositive(fromIdx, whereIdx, orderByIdx);
        return end > 0 ? end : upper.length();
    }

    /**
     * Finds the end of the FROM clause (the entity name after FROM).
     * FROM clause ends at WHERE, ORDER BY, or end of string.
     */
    private static int findFromEnd(String upper) {
        int whereIdx = indexOfKeyword(upper, " WHERE ");
        int orderByIdx = indexOfKeyword(upper, " ORDER BY ");
        int end = smallestPositive(whereIdx, orderByIdx, -1);
        return end > 0 ? end : upper.length();
    }

    private static int indexOfKeyword(String upper, String keyword) {
        return upper.indexOf(keyword);
    }

    private static int smallestPositive(int a, int b, int c) {
        int min = Integer.MAX_VALUE;
        if (a > 0) min = Math.min(min, a);
        if (b > 0) min = Math.min(min, b);
        if (c > 0) min = Math.min(min, c);
        return min == Integer.MAX_VALUE ? -1 : min;
    }

    // -- Condition parsing --

    private static JdqlQuery.JdqlCondition parseCondition(String cond) {
        String upper = cond.toUpperCase(Locale.ROOT);

        // IS NOT NULL
        if (upper.endsWith(" IS NOT NULL")) {
            String field = cond.substring(0, cond.length() - " IS NOT NULL".length()).trim();
            return new JdqlQuery.JdqlCondition(field, JdqlQuery.Operator.IS_NOT_NULL, null, null, null);
        }

        // IS NULL
        if (upper.endsWith(" IS NULL")) {
            String field = cond.substring(0, cond.length() - " IS NULL".length()).trim();
            return new JdqlQuery.JdqlCondition(field, JdqlQuery.Operator.IS_NULL, null, null, null);
        }

        // BETWEEN :min AND :max
        Pattern betweenPattern = Pattern.compile(
                "(.+?)\\s+BETWEEN\\s+(.+?)\\s+AND\\s+(.+)", Pattern.CASE_INSENSITIVE);
        Matcher betweenMatcher = betweenPattern.matcher(cond);
        if (betweenMatcher.matches()) {
            String field = betweenMatcher.group(1).trim();
            String minRef = betweenMatcher.group(2).trim();
            String maxRef = betweenMatcher.group(3).trim();
            return new JdqlQuery.JdqlCondition(field, JdqlQuery.Operator.BETWEEN,
                    extractParamOrLiteral(minRef), extractParamOrLiteral(maxRef), null);
        }

        // NOT IN :param
        Pattern notInPattern = Pattern.compile(
                "(.+?)\\s+NOT\\s+IN\\s+(.+)", Pattern.CASE_INSENSITIVE);
        Matcher notInMatcher = notInPattern.matcher(cond);
        if (notInMatcher.matches()) {
            String field = notInMatcher.group(1).trim();
            String paramRef = notInMatcher.group(2).trim();
            return new JdqlQuery.JdqlCondition(field, JdqlQuery.Operator.NOT_IN,
                    extractParamOrLiteral(paramRef), null, null);
        }

        // IN :param
        Pattern inPattern = Pattern.compile(
                "(.+?)\\s+IN\\s+(.+)", Pattern.CASE_INSENSITIVE);
        Matcher inMatcher = inPattern.matcher(cond);
        if (inMatcher.matches()) {
            String field = inMatcher.group(1).trim();
            String paramRef = inMatcher.group(2).trim();
            return new JdqlQuery.JdqlCondition(field, JdqlQuery.Operator.IN,
                    extractParamOrLiteral(paramRef), null, null);
        }

        // LIKE :param
        Pattern likePattern = Pattern.compile(
                "(.+?)\\s+LIKE\\s+(.+)", Pattern.CASE_INSENSITIVE);
        Matcher likeMatcher = likePattern.matcher(cond);
        if (likeMatcher.matches()) {
            String field = likeMatcher.group(1).trim();
            String paramRef = likeMatcher.group(2).trim();
            return new JdqlQuery.JdqlCondition(field, JdqlQuery.Operator.LIKE,
                    extractParamOrLiteral(paramRef), null, null);
        }

        // Comparison operators: >=, <=, <>, !=, >, <, =
        Pattern compPattern = Pattern.compile("(.+?)\\s*(>=|<=|<>|!=|>|<|=)\\s*(.+)");
        Matcher compMatcher = compPattern.matcher(cond);
        if (compMatcher.matches()) {
            String field = compMatcher.group(1).trim();
            String op = compMatcher.group(2);
            String valueRef = compMatcher.group(3).trim();

            JdqlQuery.Operator operator = switch (op) {
                case "=" -> JdqlQuery.Operator.EQ;
                case "<>", "!=" -> JdqlQuery.Operator.NE;
                case ">" -> JdqlQuery.Operator.GT;
                case ">=" -> JdqlQuery.Operator.GTE;
                case "<" -> JdqlQuery.Operator.LT;
                case "<=" -> JdqlQuery.Operator.LTE;
                default -> throw new IllegalArgumentException("Unknown operator: " + op);
            };

            // Check for boolean/null literals
            String upperVal = valueRef.toUpperCase(Locale.ROOT);
            if ("TRUE".equals(upperVal)) {
                return new JdqlQuery.JdqlCondition(field, operator, null, null, Boolean.TRUE);
            }
            if ("FALSE".equals(upperVal)) {
                return new JdqlQuery.JdqlCondition(field, operator, null, null, Boolean.FALSE);
            }
            if ("NULL".equals(upperVal)) {
                return new JdqlQuery.JdqlCondition(field,
                        operator == JdqlQuery.Operator.EQ ? JdqlQuery.Operator.IS_NULL : JdqlQuery.Operator.IS_NOT_NULL,
                        null, null, null);
            }

            return new JdqlQuery.JdqlCondition(field, operator,
                    extractParamOrLiteral(valueRef), null, null);
        }

        throw new IllegalArgumentException("Cannot parse JDQL condition: " + cond);
    }

    /**
     * Extracts a parameter reference or literal value from a token.
     * Parameters start with ':', literals are numbers or quoted strings.
     */
    private static String extractParamOrLiteral(String token) {
        token = token.trim();
        if (token.startsWith(":")) {
            return token; // keep the colon prefix to identify as param ref
        }
        // Numeric literal or other literal — return as-is
        return token;
    }

    // -- ORDER BY parsing --

    private static List<JdqlQuery.OrderSpec> parseOrderBy(String orderPart) {
        List<JdqlQuery.OrderSpec> specs = new ArrayList<>();
        String[] parts = orderPart.split(",");
        for (String part : parts) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            String[] tokens = p.split("\\s+");
            String field = tokens[0];
            boolean ascending = true;
            if (tokens.length > 1) {
                ascending = !"DESC".equalsIgnoreCase(tokens[1]);
            }
            specs.add(new JdqlQuery.OrderSpec(field, ascending));
        }
        return specs;
    }

    // -- Top-level AND/OR splitting (avoids splitting inside BETWEEN...AND) --

    private static boolean containsTopLevelOr(String wherePart) {
        // Simple check: find " OR " at the top level (not inside BETWEEN...AND)
        String upper = wherePart.toUpperCase(Locale.ROOT);
        int idx = upper.indexOf(" OR ");
        return idx > 0;
    }

    /**
     * Splits a WHERE clause on a top-level combinator (AND or OR).
     * Handles BETWEEN...AND by not splitting inside it.
     */
    private static List<String> splitTopLevel(String wherePart, String combinator) {
        List<String> result = new ArrayList<>();
        String upper = wherePart.toUpperCase(Locale.ROOT);
        String sep = " " + combinator + " ";
        int sepLen = sep.length();

        int start = 0;
        int idx = upper.indexOf(sep, start);

        while (idx >= 0) {
            // Check if this AND is part of BETWEEN...AND
            if ("AND".equals(combinator) && isBetweenAnd(upper, idx)) {
                idx = upper.indexOf(sep, idx + sepLen);
                continue;
            }

            result.add(wherePart.substring(start, idx));
            start = idx + sepLen;
            idx = upper.indexOf(sep, start);
        }
        result.add(wherePart.substring(start));

        return result;
    }

    /**
     * Checks if the AND at the given position is part of a BETWEEN...AND construct.
     */
    private static boolean isBetweenAnd(String upper, int andIdx) {
        // Look backwards from AND position for "BETWEEN"
        String before = upper.substring(0, andIdx);
        int betweenIdx = before.lastIndexOf("BETWEEN");
        if (betweenIdx < 0) return false;

        // Check that there's no other AND between BETWEEN and this AND
        String betweenToAnd = before.substring(betweenIdx + "BETWEEN".length());
        return !betweenToAnd.contains(" AND ");
    }
}
