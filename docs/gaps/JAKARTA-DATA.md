# Jakarta Data 1.0 -- Gap Analysis & Improvement Roadmap

> **quarkus-morphium** Jakarta Data provider
> Last updated: 2026-03-15

---

## Current State

The Jakarta Data 1.0 integration lives entirely in `quarkus-morphium` (not morphium-core).
Repository implementations are generated at **build time** via Quarkus Gizmo bytecode
generation -- no runtime reflection, GraalVM native-image compatible.

### What works today

| Area | Status | Details |
|------|--------|---------|
| Repository interfaces | Full | `DataRepository`, `BasicRepository`, `CrudRepository`, `MorphiumRepository` |
| CRUD annotations | Full | `@Insert`, `@Update`, `@Save`, `@Delete`, `@Find` |
| Query derivation | Good | 15+ operators: Equals, Not, GT/GTE/LT/LTE, Between, In, NotIn, Like, StartsWith, EndsWith, Null, NotNull, True, False |
| JDQL (`@Query`) | Good | WHERE, ORDER BY, BETWEEN, IN, LIKE, IS NULL, named params, SELECT projection, aggregate functions (COUNT/SUM/AVG/MIN/MAX). No GROUP BY/HAVING. |
| Return types | Good | `List<T>`, `Stream<T>`, `Optional<T>`, `Page<T>`, `CompletionStage<X>`, `long`, `boolean`, `void`, single `T` |
| Sorting | Full | `Sort<T>`, `Order<T>`, `@OrderBy`, JDQL ORDER BY |
| Pagination | Good | `PageRequest`, `Limit`, `MorphiumPage<T>`. No keyset/cursor pagination |
| StaticMetamodel | Full | Auto-generated `Entity_` classes for type-safe field refs |
| Morphium transparency | Full | `@Version`, `@Cache`, `@Reference`, lifecycle callbacks all work through repos |

### Key files

| File | Purpose |
|------|---------|
| `runtime/.../data/AbstractMorphiumRepository.java` | Base class for generated repo impls |
| `runtime/.../data/MorphiumRepository.java` | Morphium-specific extension interface |
| `runtime/.../data/MethodNameParser.java` | Query derivation from method names |
| `runtime/.../data/QueryDescriptor.java` | Parsed query representation |
| `runtime/.../data/QueryExecutor.java` | Query execution orchestration |
| `runtime/.../data/FindMethodBridge.java` | `@Find`/`@By`/`@OrderBy` execution |
| `runtime/.../data/JdqlParser.java` | JDQL query string parsing |
| `runtime/.../data/JdqlMethodBridge.java` | `@Query` JDQL execution |
| `runtime/.../data/MorphiumPage.java` | `Page<T>` implementation |
| `runtime/.../data/SortMapper.java` | Jakarta Data Sort -> MongoDB sort |
| `deployment/.../MorphiumDataProcessor.java` | Build-time bytecode generation (~900 LOC) |

---

## Gaps & Roadmap

### Quick Wins (< 1 day each)

#### #1 Jakarta Data Standard Exceptions
- **Status:** DONE
- **Gap:** No Jakarta Data exceptions thrown. Generic exceptions or null returned instead.
- **Required:** `EmptyResultException`, `NonUniqueResultException`, `EmptyResultException`
- **Impact:** Spec compliance, better error diagnostics for developers
- **Effort:** 2-3 hours
- **Details:** See [Detailed Plan](#1-detailed-plan-jakarta-data-standard-exceptions) below

#### #2 Missing Query Derivation Operators
- **Status:** DONE
- **Gap:** `Contains`, `Empty`, `Size`, `Matches`/`Regex`, `IgnoreCase` not supported
- **Required by spec:** Contains (collection membership), Empty (collection/string), pattern matching
- **Impact:** Users must fall back to `@Query` JDQL for these common patterns
- **Effort:** 3-4 hours
- **Files:** `MethodNameParser.java`, `QueryExecutor.java`

#### #3 `deleteAll()` no-arg + `deleteBy*` Query Derivation
- **Status:** DONE
- **Gap:** `deleteAll()` (no-arg, delete entire collection) not implemented. `deleteBy*` method name prefix not tested/verified in query derivation.
- **Impact:** Standard CrudRepository method missing
- **Effort:** 2 hours
- **Files:** `AbstractMorphiumRepository.java`, `MorphiumDataProcessor.java`, `QueryMethodBridge.java`

#### #4 Test Coverage Extension
- **Status:** DONE
- **Gap:** Untested operators: StartsWith, EndsWith, Like (with wildcards), In, NotIn, Null/IsNull, OR combinator, deleteBy derivation, multiple OrderBy fields, exception scenarios
- **Impact:** Quality assurance, regression safety
- **Effort:** 3-4 hours
- **Files:** `integration-tests/src/test/java/.../MorphiumData*Test.java`

### Medium Effort (1-2 days each)

#### #5 `CursoredPage<T>` (Keyset Pagination)
- **Status:** DONE
- **Gap:** Only offset-based pagination (`Page<T>` + `PageRequest`). No keyset/cursor pagination.
- **Why it matters:** Offset pagination degrades on large collections (`skip(100000)` is slow in MongoDB). Keyset pagination uses indexed field values for O(1) page jumps.
- **Effort:** 1-2 days
- **Files:** New `MorphiumCursoredPage.java`, changes to `FindMethodBridge.java`, `MorphiumDataProcessor.java`

#### #6 Stream Support in Repositories
- **Status:** DONE
- **Gap:** `Stream<T>` return type works but delegates to `asList().stream()` (eager loading). Should use `Query.stream()` (lazy cursor-backed) for large result sets.
- **Impact:** Memory-efficient processing of large collections via repos
- **Effort:** 0.5 days
- **Files:** `AbstractMorphiumRepository.java`, `FindMethodBridge.java`

#### #7 JDQL `SELECT` with Projection
- **Status:** DONE
- **Gap:** JDQL always returns full entities. `SELECT name, price FROM Product WHERE ...` not supported.
- **Impact:** Network/memory savings for queries that only need a few fields
- **Effort:** 1 day
- **Files:** `JdqlQuery.java`, `JdqlParser.java`, `JdqlMethodBridge.java`

### Larger Effort (3+ days)

#### #8 JDQL Aggregate Functions
- **Status:** DONE (v1 — global aggregation without GROUP BY)
- **Gap:** `COUNT()`, `SUM()`, `AVG()`, `MIN()`, `MAX()` not supported in JDQL
- **Impact:** Analytics queries require dropping down to Morphium Aggregation API
- **Effort:** 2-3 days
- **Files:** `JdqlQuery.java`, `JdqlParser.java`, `JdqlMethodBridge.java`
- **v1 supports:** `SELECT COUNT(this)`, `SELECT SUM(field)`, `SELECT AVG(field)`, `SELECT MIN(field)`, `SELECT MAX(field)` with WHERE clauses. Return types: `long` for COUNT, `double` for SUM/AVG/MIN/MAX.
- **v1 limitations:**
  - No GROUP BY (requires DTO/Record return types — own ticket)
  - No HAVING (depends on GROUP BY)
  - `COUNT(field)` behaves like `COUNT(this)` (doesn't filter null values)
  - No mixed SELECT (aggregate + field projections)
  - No `COUNT(DISTINCT ...)` or expressions inside aggregates
  - ORDER BY and pagination are ignored (single-result global aggregation)

##### #8 v1 Known Gaps (GAP-A1 through GAP-A8)

These are **deliberate scope decisions** for the v1 implementation, not bugs.
Each gap documents: what's missing, why, the required effort, and workarounds.

###### GAP-A1: No GROUP BY

**What's missing:** `SELECT status, COUNT(this) FROM Order GROUP BY status` — grouping by
fields with per-group aggregation.

**Why not in v1:**
- GROUP BY requires **DTO/Record return types** (e.g. `record StatusCount(String status, long count)`)
  because the result is no longer an entity
- `MorphiumDataProcessor` would need build-time Record analysis: discover constructor parameters,
  match them by name/position to aggregate result fields, generate mapping bytecode
- MongoDB `$group` with `_id: "$status"` returns `List<Map>` — these must be mapped to DTOs
- The Gizmo bytecode for Record instantiation needs `MethodDescriptor` for the canonical constructor

**Effort:** 2–3 days (the Record mapping infrastructure is reusable for GAP-A5)

**MongoDB pipeline equivalent:**
```json
[
  { "$group": { "_id": "$status", "count": { "$sum": 1 } } },
  { "$project": { "status": "$_id", "count": 1, "_id": 0 } }
]
```

**Workaround:** Use Morphium Aggregation API directly:
```java
@Inject Morphium morphium;
var agg = morphium.createAggregator(OrderEntity.class, Map.class);
agg.group("$status").sum("count", 1).end();
List<Map<String, Object>> results = agg.aggregateMap();
```

**Implementation notes for v2:**
- New record in `JdqlQuery`: `GroupBySpec(List<String> groupFields)`
- Parser: detect `GROUP BY` keyword after WHERE clause
- `JdqlMethodBridge.executeAggregate()`: set `_id` to group field(s) instead of `null`
- New helper: `mapAggregateResultToRecord(Map<String,Object>, Class<R> recordType)`
- `MorphiumDataProcessor`: detect Record return type, generate mapping bytecode

---

###### GAP-A2: No HAVING

**What's missing:** `SELECT status, COUNT(this) FROM Order GROUP BY status HAVING COUNT(this) > 5`

**Why not in v1:** HAVING is a post-group filter. Without GROUP BY (GAP-A1) it's meaningless.
MongoDB implements this as a `$match` stage **after** `$group`.

**Depends on:** GAP-A1

**Effort:** 0.5 days once GAP-A1 is done

**MongoDB pipeline equivalent:**
```json
[
  { "$group": { "_id": "$status", "cnt": { "$sum": 1 } } },
  { "$match": { "cnt": { "$gt": 5 } } }
]
```

---

###### GAP-A3: COUNT(field) Does Not Filter NULL Values

**What's missing:** `SELECT COUNT(customerId) WHERE status = 'OPEN'` should count only
documents where `customerId IS NOT NULL` (standard SQL COUNT semantics). Our v1 counts
**all** matching documents (identical to `COUNT(this)`).

**Why not in v1:**
- Correct implementation requires `$cond` in the `$group` accumulator:
  ```json
  { "$group": { "_id": null, "agg_0": { "$sum": { "$cond": [{ "$ne": ["$customerId", null] }, 1, 0] } } } }
  ```
- Morphium's `Group.sum()` accepts only simple field references or constants, not `$cond` expressions
- Alternative: add an extra `$match` stage with `{field: {$ne: null}}` — but this distorts the
  WHERE semantics when other aggregates run in parallel (they'd also be filtered)

**Effort:** 0.5 days — requires either `Group.sum(String, Map)` for raw expressions, or a
raw pipeline stage via `Aggregator.addOperator()`

**Behaviour in v1:** `COUNT(field)` is treated identically to `COUNT(this)`.

---

###### GAP-A4: No Mixed SELECT (Aggregate + Field Projections)

**What's missing:** `SELECT status, SUM(amount) WHERE ...` — mixing normal fields and
aggregate functions in the same SELECT clause.

**Why not in v1:**
- Mixed SELECT only makes sense with GROUP BY (e.g. `SELECT status, SUM(amount) GROUP BY status`)
- Without GROUP BY the result is undefined: which `status` value should be returned alongside
  a global SUM?
- Depends on GAP-A1 (GROUP BY) and GAP-A5 (DTO mapping)

**Behaviour in v1:** `IllegalArgumentException` with message:
"Mixing aggregate functions and field projections requires GROUP BY (not supported in v1)"

---

###### GAP-A5: No DTO/Record Return Types for Aggregate Results

**What's missing:** Instead of `Object[]` for multiple aggregates, a type-safe Record like
`record OrderStats(long count, double totalAmount)` would be preferable.

**Why not in v1:**
- Requires build-time analysis of the Record type and mapping aggregate results to Record fields
- Field order in the Record must match the SELECT order
- Shared infrastructure with GAP-A1 (GROUP BY also needs Record mapping)

**Effort:** 1–2 days (combined with GAP-A1)

**Workaround:**
```java
@Query("SELECT COUNT(this), SUM(amount) WHERE status = :s")
Object[] countAndSum(@Param("s") String status);
// Object[0] = Long (count), Object[1] = Double (sum)
```

---

###### GAP-A6: No DISTINCT or Expressions Inside Aggregates

**What's missing:**
- `SELECT COUNT(DISTINCT status) WHERE ...` — DISTINCT within aggregates
- `SELECT SUM(amount * quantity) WHERE ...` — arithmetic expressions within aggregates

**Why not in v1:**
- DISTINCT requires `$addToSet` + `$size` in the pipeline — complex mapping
- Expressions require `$multiply`/`$add` etc. inside `$group` accumulators
- Parser would need to handle arithmetic expressions within function parentheses

**Effort:** 2+ days

**MongoDB pipeline for COUNT DISTINCT:**
```json
[
  { "$group": { "_id": null, "distinctStatuses": { "$addToSet": "$status" } } },
  { "$project": { "count": { "$size": "$distinctStatuses" } } }
]
```

---

###### GAP-A7: ORDER BY Ignored for Aggregates

**What's missing:** `SELECT SUM(amount) WHERE status = :s ORDER BY amount` — ORDER BY
combined with aggregate queries.

**Why irrelevant in v1:** Global aggregation (`_id: null`) produces exactly **one** result.
Sorting a single result is a no-op. ORDER BY is silently ignored for aggregate queries.

**Becomes relevant with GROUP BY (GAP-A1):**
`SELECT status, SUM(amount) GROUP BY status ORDER BY SUM(amount) DESC` — requires a
`$sort` stage after `$group`. The sort field must reference the aggregate alias.

---

###### GAP-A8: No Pagination for Aggregates

**What's missing:** `PageRequest` / `CursoredPage` in combination with aggregate queries.

**Why irrelevant in v1:** Same as GAP-A7 — global aggregation returns 1 result. Pagination
is only meaningful with GROUP BY (GAP-A1) when there are many groups.

**Behaviour in v1:** `PageRequest` parameter is ignored for aggregate queries.

---

#### #9 `CompletionStage<T>` (Async Repositories)
- **Status:** DONE
- **Gap:** No async/reactive return types. All repository methods are synchronous.
- **Impact:** Non-blocking repository methods for reactive Quarkus applications
- **Effort:** 1 day
- **Files:** `MorphiumDataProcessor.java`, `AbstractMorphiumRepository.java`, `QueryMethodBridge.java`, `FindMethodBridge.java`, `JdqlMethodBridge.java`
- **What works:** `CompletionStage<List<T>>`, `CompletionStage<Optional<T>>`, `CompletionStage<Long>` (aggregates) for query derivation (`findBy*Async`), `@Find` annotated methods, and `@Query` JDQL methods.
- **Convention:** Query derivation methods use `*Async` suffix (e.g. `findByStatusAsync`). The "Async" suffix is stripped before method name parsing.
- **Implementation:** Async execution via `CompletableFuture.supplyAsync()` on Morphium's virtual-thread-backed `asyncOperationsThreadPool`.
- **v1 limitations:**
  - No `Uni<T>` / SmallRye Mutiny support (would need Mutiny dependency)
  - No async CRUD methods (standard `findById`, `save`, etc.) — only custom query/find/jdql methods
  - No `CompletionStage<Stream<T>>` (Stream is inherently pull-based, conflicts with async push model)

---

## Not Planned

| Feature | Reason |
|---------|--------|
| Jakarta NoSQL support | Spec too immature (v1.0, 1 impl, not in EE 11). Morphium's annotation model is richer. See analysis in session 2026-03-14. |
| `PageableRepository` interface | Deprecated pattern in Jakarta Data 1.0; pagination via method params (`PageRequest`, `Limit`) is the recommended approach and already supported. |
| JDQL JOINs | MongoDB has no native JOINs. `$lookup` is aggregation-only and doesn't map to JDQL semantics. |
| JDQL subqueries | Same reason -- no native support in MongoDB query language. |

---

## #1 Detailed Plan: Jakarta Data Standard Exceptions

### Goal

Throw the correct Jakarta Data exceptions from repository method executions so that
application code can catch standardized exception types instead of getting `null`,
generic `IllegalStateException`, or raw Morphium exceptions.

### Jakarta Data Exception Types

From `jakarta.data.exceptions` (verified in `jakarta.data-api:1.0.0`):

| Exception | When to throw |
|-----------|--------------|
| `EmptyResultException` | A query that expects exactly one result finds none (e.g., `findByEmail(...)` returning `T` not `Optional<T>`, or `findById(K)` returning `T`) |
| `NonUniqueResultException` | A query that expects at most one result finds multiple |
| `EntityExistsException` | Insert fails because entity with same ID already exists |
| `OptimisticLockingFailureException` | `@Version` conflict on update/delete |
| `MappingException` | Entity mapping/conversion fails |
| `DataConnectionException` | Database not reachable |
| `DataException` | Base class for all Jakarta Data exceptions |

**Note:** There is no `EmptyResultException` in the spec. `EmptyResultException` covers both
query-no-result and findById-not-found scenarios.

### Current Behavior (what's wrong)

1. **`FindMethodBridge.java:136`** -- single-entity queries return `null` when not found
   - Should throw `EmptyResultException` if return type is `T` (non-Optional, non-null)
   - Should return `Optional.empty()` if return type is `Optional<T>` (already correct)

2. **`FindMethodBridge.java`** -- no check for multiple results on single-entity return
   - `query.get()` returns the first match silently even if 100 rows match
   - Should throw `NonUniqueResultException` if >1 result and return type is `T` or `Optional<T>`

3. **`AbstractMorphiumRepository.java:doFindById()`** -- returns `Optional.empty()` (correct for Optional)
   - But generated code for `T findById(K)` (non-Optional) also returns null → should throw `EmptyResultException`

4. **`JdqlMethodBridge.java`** -- same issues as FindMethodBridge for single-result queries

5. **No `MappingException` wrapping** -- deserialization errors from `ObjectMapperImpl` bubble up as raw exceptions

### Implementation Plan

#### Step 1: Add jakarta.data-api dependency check (verify version)

File: `quarkus-morphium/pom.xml`
- Verify `jakarta.data:jakarta.data-api:1.0.0` is on classpath (already present)
- Verify exception classes are available: `jakarta.data.exceptions.EmptyResultException`, `NonUniqueResultException`, `EmptyResultException`, `MappingException`

#### Step 2: Modify `FindMethodBridge.java`

**Single-entity return type (`T`, not `Optional<T>`):**

```java
// Current (line ~136):
T result = query.get();
return result;  // returns null silently

// New:
List<T> results = query.limit(2).asList();
if (results.isEmpty()) {
    throw new EmptyResultException("Query returned no result");
}
if (results.size() > 1) {
    throw new NonUniqueResultException("Query returned more than one result");
}
return results.get(0);
```

**Optional return type (`Optional<T>`):**

```java
// Current:
return Optional.ofNullable(query.get());

// New:
List<T> results = query.limit(2).asList();
if (results.size() > 1) {
    throw new NonUniqueResultException("Query returned more than one result");
}
return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
```

**List/Stream/Page return types:** No changes needed -- multiple results are expected.

#### Step 3: Modify `JdqlMethodBridge.java`

Same pattern as FindMethodBridge for single-result JDQL queries.

#### Step 4: Modify `AbstractMorphiumRepository.java`

**`doFindById(K id)` method:**

```java
// Current:
T entity = morphium.findById(entityClass, id);
return Optional.ofNullable(entity);

// Keep as-is for Optional<T> return type.
// But generated code for T findById(K) needs unwrapping with exception:
```

**In `MorphiumDataProcessor.java` (generated findById code):**

When the declared return type is `T` (not `Optional`), the generated bytecode should:
1. Call `doFindById(id)` which returns `Optional<T>`
2. Call `.orElseThrow(() -> new EmptyResultException("No entity found for given id"))`

#### Step 5: Add tests

New test class: `MorphiumDataExceptionTest.java` in integration-tests:

| Test | Scenario | Expected |
|------|----------|----------|
| `findSingle_noResult_throwsEmptyResult` | `findByEmail("nonexistent")` returns `T` | `EmptyResultException` |
| `findSingle_multipleResults_throwsNonUnique` | `findByCategory("common")` returns `T` with 5 matches | `NonUniqueResultException` |
| `findOptional_noResult_returnsEmpty` | `findOptionalByEmail("nonexistent")` | `Optional.empty()` (no exception) |
| `findOptional_multipleResults_throwsNonUnique` | `findOptionalByCategory("common")` returns `Optional<T>` | `NonUniqueResultException` |
| `findById_notFound_throwsEmptyResult` | `findById("missing-id")` returns `T` | `EmptyResultException` |
| `findById_notFound_optional_returnsEmpty` | `findByIdOptional("missing-id")` | `Optional.empty()` |
| `jdql_noResult_throwsEmptyResult` | `@Query("WHERE email = :e")` returns `T` | `EmptyResultException` |

#### Step 6: Verify exception class availability

Check whether `jakarta.data.exceptions` package is in the `jakarta.data-api:1.0.0` JAR.
If the exception classes don't exist in 1.0.0 (they may have been added in 1.0.1 or later),
we define our own that extend `DataException`.

### Files to modify

| File | Change |
|------|--------|
| `FindMethodBridge.java` | Add uniqueness check + EmptyResultException for single-entity returns |
| `JdqlMethodBridge.java` | Same pattern for JDQL single-result queries |
| `AbstractMorphiumRepository.java` | No change (Optional return already correct) |
| `MorphiumDataProcessor.java` | Generate `orElseThrow(EmptyResultException)` for non-Optional findById |
| New: `MorphiumDataExceptionTest.java` | 7 integration tests |
| New: test repository interface with single-return methods | Test fixture |

### Acceptance Criteria

- [ ] `T findByX(...)` throws `EmptyResultException` when no result
- [ ] `T findByX(...)` throws `NonUniqueResultException` when >1 result
- [ ] `Optional<T> findByX(...)` returns `Optional.empty()` when no result (no exception)
- [ ] `Optional<T> findByX(...)` throws `NonUniqueResultException` when >1 result
- [ ] `T findById(K)` (non-Optional return) throws `EmptyResultException` when not found
- [ ] `Optional<T> findById(K)` returns `Optional.empty()` (no exception)
- [ ] `@Query` JDQL single-result methods follow the same rules
- [ ] `List<T>`, `Stream<T>`, `Page<T>` return types are unaffected
- [ ] All 7 integration tests pass
- [ ] Existing integration tests remain green
