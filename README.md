# Quarkus Morphium Extension

[![Build](https://github.com/Bardioc1977/quarkus-morphium/actions/workflows/build.yml/badge.svg)](https://github.com/Bardioc1977/quarkus-morphium/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.32.1-blue)](https://quarkus.io)
[![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://adoptium.net)
[![Documentation](https://img.shields.io/badge/docs-GitHub%20Pages-brightgreen)](https://bardioc1977.github.io/quarkus-morphium/)

A [Quarkus](https://quarkus.io) CDI extension for [Morphium](https://github.com/sboesebeck/morphium),
an actively maintained MongoDB ORM for Java.

**[Read the full documentation](https://bardioc1977.github.io/quarkus-morphium/)**

## Features

- **Zero-boilerplate CDI integration** – inject `Morphium` directly via `@Inject`; the extension manages the lifecycle
- **Declarative transactions** – annotate methods with `@MorphiumTransactional` for automatic commit/rollback, with CDI events for transaction lifecycle hooks
- **Type-safe configuration** – all settings live in `application.properties` under the `quarkus.morphium.*` prefix
- **Dev Services** – automatic MongoDB container in dev/test mode, with optional single-node replica set for transactions
- **Health checks** – MicroProfile liveness, readiness, and startup probes with connection pool metadata
- **SSL/TLS & X.509** – encrypted connections and client-certificate authentication via `quarkus.morphium.ssl.*`
- **Dev UI card** – MongoDB connection info displayed in the Quarkus Dev UI at `/q/dev-ui/`
- **Blocking call detection** – warns when Morphium writes are called from the Vert.x event loop
- **GraalVM native ready** – all `@Entity` and `@Embedded` classes are registered for reflection at build time; no manual `reflect-config.json` entries needed
- **Test-friendly** – use `quarkus.morphium.driver-name=InMemDriver` in your test profile for fast, in-process tests without a running MongoDB
- **Clean JDK 25 implementation** – no `sun.*` imports, no `Unsafe` access, no `--add-opens` for internal APIs
- **CosmosDB compatibility** – `@MorphiumTransactional` gracefully degrades on Azure CosmosDB (auto-detected); individual ops remain atomic, only multi-document rollback is unavailable
- **Graceful shutdown** – `Morphium.close()` is called automatically when the application stops

## Prerequisites

| Dependency | Minimum version |
|---|---|
| Java | 21 |
| Quarkus | 3.32.1 |
| Morphium | 6.2.0 |

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

## Configuration

Add the following to `src/main/resources/application.properties`:

```properties
# Required
quarkus.morphium.database=my-database

# MongoDB hosts (comma-separated host:port pairs, default: localhost:27017)
quarkus.morphium.hosts=mongo1:27017,mongo2:27017

# Credentials (optional)
quarkus.morphium.username=admin
quarkus.morphium.password=secret
quarkus.morphium.auth-database=admin

# Connection pool (default: 250)
quarkus.morphium.max-connections=100

# MongoDB Atlas – overrides quarkus.morphium.hosts when present
# quarkus.morphium.atlas-url=mongodb+srv://user:pass@cluster.mongodb.net/

# Read preference: primary | primaryPreferred | secondary | secondaryPreferred | nearest
quarkus.morphium.read-preference=primary

# Automatically create/verify indexes on startup (default: true)
quarkus.morphium.create-indexes=true

# Morphium driver: PooledDriver (production) | InMemDriver (tests)
quarkus.morphium.driver-name=PooledDriver

# Query result cache
quarkus.morphium.cache.read-cache-enabled=true
quarkus.morphium.cache.global-valid-time=60000

# LocalDateTime storage format (default: true = BSON ISODate)
# Set to false only when reading data written by legacy Morphium (Map format {sec, n})
quarkus.morphium.local-date-time.use-bson-date=true
```

### Full configuration reference

| Property | Default | Description |
|---|---|---|
| `quarkus.morphium.database` | *(required)* | MongoDB database name |
| `quarkus.morphium.hosts` | `localhost:27017` | Comma-separated `host:port` list |
| `quarkus.morphium.username` | – | MongoDB username |
| `quarkus.morphium.password` | – | MongoDB password |
| `quarkus.morphium.auth-database` | `admin` | Authentication database |
| `quarkus.morphium.atlas-url` | – | MongoDB Atlas SRV URL (overrides `hosts`) |
| `quarkus.morphium.read-preference` | `primary` | Read preference |
| `quarkus.morphium.create-indexes` | `true` | Create indexes on startup |
| `quarkus.morphium.max-connections` | `250` | Connection pool size |
| `quarkus.morphium.driver-name` | `PooledDriver` | Morphium driver name |
| `quarkus.morphium.cache.read-cache-enabled` | `true` | Enable query result cache |
| `quarkus.morphium.cache.global-valid-time` | `60000` | Cache TTL in milliseconds |
| `quarkus.morphium.local-date-time.use-bson-date` | `true` | Store `LocalDateTime` as BSON `ISODate`; set `false` for legacy Morphium Map format |
| `quarkus.morphium.ssl.enabled` | `false` | Enable TLS for the MongoDB connection |
| `quarkus.morphium.ssl.auth-mechanism` | – | Authentication mechanism (`MONGODB-X509` for X.509 client-cert auth) |
| `quarkus.morphium.ssl.keystore-path` | – | Path to keystore (JKS/PKCS12) for X.509 / mutual TLS |
| `quarkus.morphium.ssl.keystore-password` | – | Keystore password |
| `quarkus.morphium.ssl.truststore-path` | – | Path to truststore for server certificate validation |
| `quarkus.morphium.ssl.truststore-password` | – | Truststore password |
| `quarkus.morphium.ssl.invalid-hostname-allowed` | `false` | Allow self-signed hostnames (dev only) |
| `quarkus.morphium.ssl.x509-username` | – | Explicit X.509 subject DN override |
| `quarkus.morphium.devservices.replica-set` | `false` | Start single-node replica set (enables transactions) |
| `quarkus.morphium.health.enabled` | `true` | Enable Morphium health checks (liveness, readiness, startup) |

For the complete configuration reference including detailed descriptions, see the
[Configuration Reference](https://bardioc1977.github.io/quarkus-morphium/quarkus-morphium/dev/configuration.html).

## Usage

### Define an entity

```java
import de.caluga.morphium.annotations.*;
import de.caluga.morphium.annotations.lifecycle.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@NoArgsConstructor
@Entity(collectionName = "products")
@Lifecycle
public class ProductEntity {

    @Id
    private String id;

    @Property(fieldName = "name")
    private String name;

    @Property(fieldName = "price")
    private double price;

    @Version
    @Property(fieldName = "version")
    private long version;

    @Property(fieldName = "created_at")
    private Instant createdAt;

    @PreStore
    public void onStore() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
```

### Inject and use Morphium

```java
import de.caluga.morphium.Morphium;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ProductRepository {

    @Inject
    Morphium morphium;

    public void save(ProductEntity product) {
        morphium.store(product);   // sets id and version in-place
    }

    public Optional<ProductEntity> findByName(String name) {
        return Optional.ofNullable(
            morphium.createQueryFor(ProductEntity.class)
                .f("name").eq(name)
                .get()
        );
    }

    public List<ProductEntity> findAll() {
        return morphium.createQueryFor(ProductEntity.class).asList();
    }

    public void delete(ProductEntity product) {
        morphium.delete(product);
    }
}
```

### Embedded documents

```java
@Data
@NoArgsConstructor
@Embedded
public class AddressEmbedded {
    @Property(fieldName = "street") private String street;
    @Property(fieldName = "city")   private String city;
}
```

## Declarative transactions

Annotate any CDI bean method with `@MorphiumTransactional` to wrap it in a Morphium transaction.
On success the transaction is committed; on any exception it is rolled back and the exception is
re-thrown.

```java
import de.caluga.morphium.Morphium;
import de.caluga.morphium.quarkus.transaction.MorphiumTransactional;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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

The annotation can also be placed on a class to apply to all business methods.

### Transaction lifecycle events

The interceptor fires CDI events at each transaction phase. Use `@Observes` with the
`@MorphiumTxPhase` qualifier to react:

```java
import de.caluga.morphium.quarkus.transaction.MorphiumTransactionEvent;
import de.caluga.morphium.quarkus.transaction.MorphiumTxPhase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import static de.caluga.morphium.quarkus.transaction.MorphiumTransactionEvent.Phase.*;

@ApplicationScoped
public class OrderNotifier {

    void afterCommit(@Observes @MorphiumTxPhase(AFTER_COMMIT) MorphiumTransactionEvent e) {
        // send confirmation email, publish domain event, etc.
    }

    void afterRollback(@Observes @MorphiumTxPhase(AFTER_ROLLBACK) MorphiumTransactionEvent e) {
        log.warn("Transaction rolled back", e.getFailure());
    }
}
```

| Phase | Fired when | `getFailure()` |
|---|---|---|
| `BEFORE_COMMIT` | After the business method succeeds, before `commitTransaction()` | `null` |
| `AFTER_COMMIT` | After `commitTransaction()` succeeds | `null` |
| `AFTER_ROLLBACK` | After `abortTransaction()` due to an exception | The causing exception |

## Dev Services (automatic MongoDB container)

In **dev** (`quarkus:dev`) and **test** mode the extension automatically starts a MongoDB
container via Testcontainers when `quarkus.morphium.hosts` is **not** explicitly configured.
No `application.properties` changes are needed.

| Config key | Default | Description |
|---|---|---|
| `quarkus.morphium.devservices.enabled` | `true` | Set to `false` to disable |
| `quarkus.morphium.devservices.image-name` | `mongo:8` | Docker image to use |
| `quarkus.morphium.devservices.database-name` | `morphium-dev` | Database injected into `quarkus.morphium.database` |
| `quarkus.morphium.devservices.replica-set` | `false` | Start single-node replica set (enables transactions in dev/test) |

```properties
# Disable Dev Services and point to an external MongoDB instead:
quarkus.morphium.devservices.enabled=false
quarkus.morphium.hosts=my-mongo:27017
quarkus.morphium.database=mydb
```

## Testing without MongoDB (InMemDriver)

For **unit tests** that should run without Docker, use Morphium's built-in `InMemDriver`:

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
    void shouldSaveAndFind() {
        var p = new ProductEntity();
        p.setName("Widget");
        p.setPrice(9.99);

        repository.save(p);
        assertThat(p.getId()).isNotNull();
        assertThat(p.getVersion()).isEqualTo(1L);

        var found = repository.findByName("Widget");
        assertThat(found).isPresent();
        assertThat(found.get().getPrice()).isEqualTo(9.99);
    }
}
```

## LocalDateTime storage format

By default `LocalDateTime` fields are persisted as native BSON `ISODate` values
(`quarkus.morphium.local-date-time.use-bson-date=true`).
This enables native MongoDB date operations (range queries, sorting, `$gt`/`$lt` filters)
and displays as readable ISO timestamps in mongosh and the Atlas UI.

```properties
# application.properties

# BSON ISODate – recommended for all new projects and Morphia-compatible data (default)
quarkus.morphium.local-date-time.use-bson-date=true

# Legacy Map format {sec: epochSecond, n: nanos} – only for backward compatibility
quarkus.morphium.local-date-time.use-bson-date=false
```

### When to use which format

| | BSON `ISODate` (`true`) | Legacy Map (`false`) |
|---|---|---|
| New projects | **recommended** | – |
| Data written by Morphia | compatible | incompatible |
| Data written by Morphium ≤ 6.1 | incompatible | compatible |
| Native date queries (`$gt`, `$lt`) | yes | no |
| Readable in Atlas UI / mongosh | yes | no |

### Migrating from the legacy Map format

If you have existing data stored in Morphium's `{sec: …, n: …}` Map format and want to
migrate to BSON `ISODate`, run a one-off migration script **before** switching the flag:

```javascript
// mongosh – run once per collection that contains LocalDateTime fields
db.my_collection.find({ created_at: { $type: "object" } }).forEach(doc => {
  const dt = new Date(doc.created_at.sec * 1000);
  db.my_collection.updateOne(
    { _id: doc._id },
    { $set: { created_at: dt } }
  );
});
```

After the migration, set `quarkus.morphium.local-date-time.use-bson-date=true` (or remove the
property, as `true` is the default).

## Building from source

```bash
# Build and install locally (requires Morphium 6.2.0-SNAPSHOT)
mvn install

# Skip tests
mvn install -DskipTests

# Verify formatting and full build
mvn verify
```

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

This project follows the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md).

## License

This project is licensed under the [Apache License 2.0](LICENSE).

## Documentation

Comprehensive documentation is available as an Antora-generated site:

**[quarkus-morphium Documentation](https://bardioc1977.github.io/quarkus-morphium/)**

The documentation covers:
- [Getting Started](https://bardioc1977.github.io/quarkus-morphium/quarkus-morphium/dev/getting-started.html) – installation, first entity, first query
- [Configuration Reference](https://bardioc1977.github.io/quarkus-morphium/quarkus-morphium/dev/configuration.html) – all properties including SSL/TLS
- [Entities & Annotations](https://bardioc1977.github.io/quarkus-morphium/quarkus-morphium/dev/entities.html) – `@Entity`, `@Embedded`, lifecycle hooks
- [Transactions](https://bardioc1977.github.io/quarkus-morphium/quarkus-morphium/dev/transactions.html) – `@MorphiumTransactional`, CDI events
- [Dev Services](https://bardioc1977.github.io/quarkus-morphium/quarkus-morphium/dev/dev-services.html) – automatic MongoDB container, replica-set mode
- [Health Checks](https://bardioc1977.github.io/quarkus-morphium/quarkus-morphium/dev/health-checks.html) – MicroProfile probes, Kubernetes mapping
- [Testing](https://bardioc1977.github.io/quarkus-morphium/quarkus-morphium/dev/testing.html) – Dev Services vs. InMemDriver strategies
- [Advanced Topics](https://bardioc1977.github.io/quarkus-morphium/quarkus-morphium/dev/advanced.html) – SSL/TLS, Atlas SRV, blocking detection, GraalVM

## Related projects

- [Morphium](https://github.com/sboesebeck/morphium) – the underlying MongoDB ORM
- [Quarkus](https://quarkus.io) – the supersonic, subatomic Java framework
- [Quarkiverse Hub](https://github.com/quarkiverse) – community Quarkus extensions

