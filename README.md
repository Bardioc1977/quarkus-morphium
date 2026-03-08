# Quarkus Morphium Extension

[![Build](https://github.com/Bardioc1977/quarkus-morphium/actions/workflows/build.yml/badge.svg)](https://github.com/Bardioc1977/quarkus-morphium/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.32.1-blue)](https://quarkus.io)
[![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://adoptium.net)
[![Jakarta Data](https://img.shields.io/badge/Jakarta%20Data-1.0-green)](https://jakarta.ee/specifications/data/1.0/)
[![Documentation](https://img.shields.io/badge/docs-GitHub%20Pages-brightgreen)](https://bardioc1977.github.io/quarkus-morphium/dev/index.html)

> **Note:** This extension is built on the [Bardioc1977/morphium](https://github.com/Bardioc1977/morphium) fork
> (currently **6.2.0-SNAPSHOT**). Fork improvements are being contributed back to the
> upstream [sboesebeck/morphium](https://github.com/sboesebeck/morphium) project via pull requests and are
> progressively merged. See also: [quarkus-morphium-showcase](https://github.com/Bardioc1977/quarkus-morphium-showcase).

A [Quarkus](https://quarkus.io) CDI extension for [Morphium](https://github.com/sboesebeck/morphium),
an actively maintained MongoDB ORM for Java — with full **Jakarta Data 1.0** support.

**[Read the full documentation](https://bardioc1977.github.io/quarkus-morphium/dev/index.html)** | **[Live Showcase](https://github.com/Bardioc1977/quarkus-morphium-showcase)**

---

## Jakarta Data 1.0 — Declarative Repositories for MongoDB

Define a `@Repository` interface, inject it, done. The extension generates the implementation
at **Quarkus build time** via Gizmo bytecode generation — no runtime reflection, no proxies,
GraalVM native-image compatible.

```java
@Repository
public interface ProductRepository extends CrudRepository<Product, MorphiumId> {

    List<Product> findByCategory(String category);

    @OrderBy("price")
    List<Product> findByPriceBetween(double min, double max);

    long countByCategory(String category);

    boolean existsByName(String name);

    Page<Product> findByCategory(String category, PageRequest page);

    @Find
    List<Product> search(@By("category") String cat,
                         @By("price") @Is(GreaterThanEqual) double minPrice,
                         Sort<Product> sort);

    @Query("WHERE category = :cat AND price > :minPrice ORDER BY price")
    List<Product> findExpensive(@Param("cat") String category,
                                @Param("minPrice") double minPrice);
}
```

```java
@ApplicationScoped
public class ProductService {

    @Inject ProductRepository products;

    public Page<Product> browse(int page, int size) {
        return products.findByCategory("electronics",
            PageRequest.ofPage(page, size, true));
    }
}
```

### What's supported

| Feature | Details |
|---------|---------|
| **CRUD** | `CrudRepository<T,K>`, `BasicRepository<T,K>`, `DataRepository<T,K>` — save, insert, update, delete, findById, findAll, existsById |
| **Query derivation** | `findBy`, `countBy`, `existsBy`, `deleteBy` with operators: Equals, Not, GreaterThan, LessThan, Between, In, NotIn, Like, StartsWith, EndsWith, Null, NotNull, True, False — combined with And/Or |
| **@Find + @By** | Explicit field binding via parameter annotations, combined with `@Is(Operator)` for non-equality conditions |
| **@Query (JDQL)** | Jakarta Data Query Language with WHERE, ORDER BY, named parameters (`:param`), comparison operators, BETWEEN, IN, LIKE, IS NULL |
| **@OrderBy** | Static sort annotation on query methods |
| **Pagination** | `Page<T>`, `PageRequest` with total counts, `Limit` |
| **Sorting** | `Sort<T>`, `Order<T>` as method parameters |
| **@StaticMetamodel** | Auto-generated `Entity_` classes with `Attribute`, `SortableAttribute`, `TextAttribute` fields — type-safe field references |
| **Build-time validation** | Entity fields, ID types, method signatures validated during `mvn compile` — fail fast, not at runtime |

All Morphium ORM features work transparently through generated repositories: `@Version`
(optimistic locking), `@CreationTime`/`@LastChange`, lifecycle callbacks (`@PreStore`,
`@PostLoad`), `@Cache`, `@WriteBuffer`, and `@Reference` (lazy/eager) — because the
generated implementation delegates to `morphium.store()`, `morphium.findById()` etc.

The imperative Morphium API (`@Inject Morphium`) remains fully available for aggregation pipelines,
bulk updates, atomic `inc`/`push`/`pull` operations, distinct queries, and anything beyond standard CRUD.

---

## All Features

### CDI & Lifecycle
- **Zero-boilerplate CDI integration** — inject `Morphium` or any `@Repository` interface directly via `@Inject`
- **Declarative transactions** — `@MorphiumTransactional` with automatic commit/rollback and CDI lifecycle events (`BEFORE_COMMIT`, `AFTER_COMMIT`, `AFTER_ROLLBACK`)
- **Graceful shutdown** — `Morphium.close()` called automatically on application stop

### Developer Experience
- **Type-safe configuration** — all settings under `quarkus.morphium.*` in `application.properties`
- **Dev Services** — automatic MongoDB container in dev/test mode via Testcontainers, with optional single-node replica set for transactions
- **Dev UI card** — MongoDB connection info in the Quarkus Dev UI at `/q/dev-ui/`
- **Test-friendly** — `quarkus.morphium.driver-name=InMemDriver` for fast, in-process tests without Docker
- **Blocking call detection** — warns when Morphium writes happen on the Vert.x event loop

### Production
- **Health checks** — MicroProfile liveness, readiness, and startup probes with connection pool metadata
- **SSL/TLS & X.509** — encrypted connections and client-certificate authentication via `quarkus.morphium.ssl.*`
- **GraalVM native ready** — all `@Entity` and `@Embedded` classes registered for reflection at build time
- **CosmosDB compatibility** — `@MorphiumTransactional` gracefully degrades on Azure CosmosDB (auto-detected); supports all Azure sovereign clouds

### Morphium ORM
- **@Reference cascade** — `cascadeDelete` and `orphanRemoval` with automatic cycle detection for bidirectional references
- **Built-in caching** — `@Cache` and `@WriteBuffer` annotations for read cache and async write batching
- **Lifecycle hooks** — `@PreStore`, `@PostStore`, `@PostLoad` etc. on `@Entity` classes
- **Optimistic locking** — `@Version` for concurrent modification detection
- **Schema evolution** — `@Aliases` for legacy field name compatibility

---

## Prerequisites

| Dependency | Minimum version |
|---|---|
| Java | 21 |
| Quarkus | 3.32.1 |
| Morphium | 6.2.0-SNAPSHOT ([Bardioc1977/morphium](https://github.com/Bardioc1977/morphium)) |

## Installation

Add the extension to your `pom.xml`:

```xml
<dependency>
    <groupId>io.quarkiverse.morphium</groupId>
    <artifactId>quarkus-morphium</artifactId>
    <version>1.0.0</version>
</dependency>
```

> **Note:** Until the extension is published to Maven Central via Quarkiverse, use the
> GitHub Packages registry (see below) or build locally:
> ```bash
> git clone https://github.com/Bardioc1977/quarkus-morphium.git
> cd quarkus-morphium
> mvn install -DskipTests
> ```
> Then use `groupId: io.quarkiverse.morphium`, `artifactId: quarkus-morphium`, `version: 1.0.0-SNAPSHOT`.

### GitHub Packages (interim)

Until the extension is published to Maven Central, release and SNAPSHOT artifacts are
available from the GitHub Packages Maven registry.

**1. Add the repository to your `pom.xml`:**

```xml
<repositories>
  <repository>
    <id>github-quarkus-morphium</id>
    <url>https://maven.pkg.github.com/Bardioc1977/quarkus-morphium</url>
  </repository>
</repositories>
```

**2. Configure authentication in `~/.m2/settings.xml`:**

GitHub Packages requires authentication even for public packages. Create a
[Personal Access Token](https://github.com/settings/tokens) with `read:packages` scope.

```xml
<servers>
  <server>
    <id>github-quarkus-morphium</id>
    <username>GITHUB_USERNAME</username>
    <password>GITHUB_PAT_WITH_READ_PACKAGES</password>
  </server>
</servers>
```

## Quick Start

### 1. Configure

```properties
# Required
quarkus.morphium.database=my-database

# MongoDB hosts (default: localhost:27017)
quarkus.morphium.hosts=mongo1:27017,mongo2:27017

# Or use Dev Services — no config needed, MongoDB starts automatically
```

### 2. Define an entity

```java
@Entity(collectionName = "products")
@Data @NoArgsConstructor
public class Product {
    @Id private MorphiumId id;
    private String name;
    private double price;
    private String category;
    @Version private long version;
}
```

### 3. Create a repository

```java
@Repository
public interface ProductRepository extends CrudRepository<Product, MorphiumId> {

    List<Product> findByCategory(String category);

    @OrderBy("price")
    List<Product> findByPriceGreaterThan(double minPrice);
}
```

### 4. Use it

```java
@ApplicationScoped
public class ProductService {

    @Inject ProductRepository products;

    public Product create(String name, double price, String category) {
        var product = new Product();
        product.setName(name);
        product.setPrice(price);
        product.setCategory(category);
        return products.insert(product);
    }

    public List<Product> findExpensive(double minPrice) {
        return products.findByPriceGreaterThan(minPrice);
    }
}
```

### Imperative API (always available)

For complex queries, aggregations, or atomic operations, inject `Morphium` directly:

```java
@Inject Morphium morphium;

public List<Map<String, Object>> salesByCategory() {
    return morphium.createAggregator(Product.class, Map.class)
        .group("$category").sum("total", "$price").end()
        .sort("-total")
        .aggregateMap();
}
```

## Configuration Reference

| Property | Default | Description |
|---|---|---|
| `quarkus.morphium.database` | *(required)* | MongoDB database name |
| `quarkus.morphium.hosts` | `localhost:27017` | Comma-separated `host:port` list |
| `quarkus.morphium.username` | -- | MongoDB username |
| `quarkus.morphium.password` | -- | MongoDB password |
| `quarkus.morphium.auth-database` | `admin` | Authentication database |
| `quarkus.morphium.atlas-url` | -- | MongoDB Atlas SRV URL (overrides `hosts`) |
| `quarkus.morphium.read-preference` | `primary` | Read preference |
| `quarkus.morphium.create-indexes` | `true` | Create indexes on startup |
| `quarkus.morphium.max-connections` | `250` | Connection pool size |
| `quarkus.morphium.driver-name` | `PooledDriver` | `PooledDriver` (production) or `InMemDriver` (tests) |
| `quarkus.morphium.cache.read-cache-enabled` | `true` | Enable query result cache |
| `quarkus.morphium.cache.global-valid-time` | `60000` | Cache TTL in milliseconds |
| `quarkus.morphium.local-date-time.use-bson-date` | `true` | Store `LocalDateTime` as BSON `ISODate` |
| `quarkus.morphium.ssl.enabled` | `false` | Enable TLS |
| `quarkus.morphium.ssl.auth-mechanism` | -- | `MONGODB-X509` for client-cert auth |
| `quarkus.morphium.ssl.keystore-path` | -- | Keystore path (JKS/PKCS12) |
| `quarkus.morphium.ssl.keystore-password` | -- | Keystore password |
| `quarkus.morphium.ssl.truststore-path` | -- | Truststore path |
| `quarkus.morphium.ssl.truststore-password` | -- | Truststore password |
| `quarkus.morphium.ssl.invalid-hostname-allowed` | `false` | Allow invalid hostnames (dev only) |
| `quarkus.morphium.ssl.x509-username` | -- | X.509 subject DN override |
| `quarkus.morphium.devservices.enabled` | `true` | Enable automatic MongoDB container |
| `quarkus.morphium.devservices.image-name` | `mongo:8` | Docker image for Dev Services |
| `quarkus.morphium.devservices.database-name` | `morphium-dev` | Database name in Dev Services |
| `quarkus.morphium.devservices.replica-set` | `false` | Start as replica set (enables transactions) |
| `quarkus.morphium.health.enabled` | `true` | Enable health checks |

For detailed descriptions, see the
[Configuration Reference](https://bardioc1977.github.io/quarkus-morphium/dev/configuration.html).

## Transactions

```java
@ApplicationScoped
public class OrderService {

    @Inject Morphium morphium;

    @MorphiumTransactional
    public void placeOrder(Order order, Payment payment) {
        morphium.store(order);
        morphium.store(payment);
        // auto-commit on success, auto-rollback on exception
    }
}
```

React to transaction events via CDI:

```java
void afterCommit(@Observes @MorphiumTxPhase(AFTER_COMMIT) MorphiumTransactionEvent e) {
    // send confirmation, publish domain event, ...
}
```

## Testing

```properties
# src/test/resources/application.properties
%test.quarkus.morphium.driver-name=InMemDriver
%test.quarkus.morphium.database=test-db
```

```java
@QuarkusTest
class ProductRepositoryTest {

    @Inject ProductRepository repository;

    @Test
    void shouldFindByCategory() {
        var p = new Product();
        p.setName("Widget");
        p.setCategory("tools");
        p.setPrice(9.99);
        repository.save(p);

        var results = repository.findByCategory("tools");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Widget");
    }
}
```

## Known Limitation: `sun.misc.Unsafe`

The Morphium ORM uses `sun.misc.Unsafe.allocateInstance()` to instantiate entity classes that
**do not have a no-arg constructor**. This is the de facto standard used by Spring, Jackson,
Gson, Kryo, Hibernate/Objenesis and others.

**To avoid it:** add a no-arg constructor (can be `private` or package-private) to your
`@Entity` classes. When present, Morphium uses it directly and `Unsafe` is never called.

`Unsafe.allocateInstance()` is **not** covered by [JEP 471](https://openjdk.org/jeps/471) (JDK 23).
Once a public replacement API exists, Morphium will migrate to it.

## Building from Source

```bash
# Requires Morphium 6.2.0-SNAPSHOT from Bardioc1977/morphium
mvn install

# Skip tests
mvn install -DskipTests
```

## Related Projects

- [Morphium](https://github.com/sboesebeck/morphium) — the underlying MongoDB ORM
- [quarkus-morphium-showcase](https://github.com/Bardioc1977/quarkus-morphium-showcase) — interactive demo with all features
- [Quarkus](https://quarkus.io) — supersonic, subatomic Java framework
- [Jakarta Data 1.0](https://jakarta.ee/specifications/data/1.0/) — the specification

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

This project follows the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md).

## License

[Apache License 2.0](LICENSE)
