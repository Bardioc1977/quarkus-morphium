# Quarkus Morphium Extension

[![Build](https://github.com/Bardioc1977/quarkus-morphium/actions/workflows/build.yml/badge.svg)](https://github.com/Bardioc1977/quarkus-morphium/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.32.1-blue)](https://quarkus.io)
[![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://adoptium.net)

A [Quarkus](https://quarkus.io) CDI extension for [Morphium](https://github.com/sboesebeck/morphium),
an actively maintained MongoDB ORM for Java.

## Features

- **Zero-boilerplate CDI integration** – inject `Morphium` directly via `@Inject`; the extension manages the lifecycle
- **Type-safe configuration** – all settings live in `application.properties` under the `morphium.*` prefix
- **GraalVM native ready** – all `@Entity` and `@Embedded` classes are registered for reflection at build time; no manual `reflect-config.json` entries needed
- **Test-friendly** – use `morphium.driver-name=InMemDriver` in your test profile for fast, in-process tests without a running MongoDB
- **Clean JDK 25 implementation** – no `sun.*` imports, no `Unsafe` access, no `--add-opens` for internal APIs
- **Graceful shutdown** – `Morphium.close()` is called automatically when the application stops

## Prerequisites

| Dependency | Minimum version |
|---|---|
| Java | 21 |
| Quarkus | 3.32.1 |
| Morphium | 6.1.9 |

## Installation

Add the extension to your `pom.xml`:

```xml
<dependency>
    <groupId>io.quarkiverse.morphium</groupId>
    <artifactId>quarkus-morphium</artifactId>
    <version>1.0.0</version>
</dependency>
```

> **Note:** Until the extension is published to Maven Central via Quarkiverse, build it locally:
> ```bash
> git clone https://github.com/Bardioc1977/quarkus-morphium.git
> cd quarkus-morphium
> mvn install -DskipTests
> ```
> Then use `groupId: de.caluga.morphium`, `artifactId: quarkus-morphium`, `version: 1.0.0-SNAPSHOT`.

## Configuration

Add the following to `src/main/resources/application.properties`:

```properties
# Required
morphium.database=my-database

# MongoDB hosts (comma-separated host:port pairs, default: localhost:27017)
morphium.hosts=mongo1:27017,mongo2:27017

# Credentials (optional)
morphium.username=admin
morphium.password=secret
morphium.auth-database=admin

# Connection pool (default: 250)
morphium.max-connections=100

# MongoDB Atlas – overrides morphium.hosts when present
# morphium.atlas-url=mongodb+srv://user:pass@cluster.mongodb.net/

# Read preference: primary | primaryPreferred | secondary | secondaryPreferred | nearest
morphium.read-preference=primary

# Automatically create/verify indexes on startup (default: true)
morphium.create-indexes=true

# Morphium driver: PooledDriver (production) | InMemDriver (tests)
morphium.driver-name=PooledDriver

# Query result cache
morphium.cache.read-cache-enabled=true
morphium.cache.global-valid-time=60000
```

### Full configuration reference

| Property | Default | Description |
|---|---|---|
| `morphium.database` | *(required)* | MongoDB database name |
| `morphium.hosts` | `localhost:27017` | Comma-separated `host:port` list |
| `morphium.username` | – | MongoDB username |
| `morphium.password` | – | MongoDB password |
| `morphium.auth-database` | `admin` | Authentication database |
| `morphium.atlas-url` | – | MongoDB Atlas SRV URL (overrides `hosts`) |
| `morphium.read-preference` | `primary` | Read preference |
| `morphium.create-indexes` | `true` | Create indexes on startup |
| `morphium.max-connections` | `250` | Connection pool size |
| `morphium.driver-name` | `PooledDriver` | Morphium driver name |
| `morphium.cache.read-cache-enabled` | `true` | Enable query result cache |
| `morphium.cache.global-valid-time` | `60000` | Cache TTL in milliseconds |

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

## Dev Services (automatic MongoDB container)

In **dev** (`quarkus:dev`) and **test** mode the extension automatically starts a MongoDB
container via Testcontainers when `morphium.hosts` is **not** explicitly configured.
No `application.properties` changes are needed.

| Config key | Default | Description |
|---|---|---|
| `quarkus.morphium.devservices.enabled` | `true` | Set to `false` to disable |
| `quarkus.morphium.devservices.image-name` | `mongo:8` | Docker image to use |
| `quarkus.morphium.devservices.database-name` | `morphium-dev` | Database injected into `morphium.database` |

```properties
# Disable Dev Services and point to an external MongoDB instead:
quarkus.morphium.devservices.enabled=false
morphium.hosts=my-mongo:27017
morphium.database=mydb
```

## Testing without MongoDB (InMemDriver)

For **unit tests** that should run without Docker, use Morphium's built-in `InMemDriver`:

```properties
# src/test/resources/application.properties
%test.morphium.driver-name=InMemDriver
%test.morphium.database=test-db
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

## Building from source

```bash
# Build and install locally (requires Morphium 6.1.9-SNAPSHOT)
mvn install

# Skip tests
mvn install -DskipTests

# Verify formatting and full build
mvn verify
```

## Contributing

Contributions are welcome. Please open an issue before submitting a pull request for significant changes.

This project follows the [Contributor Covenant Code of Conduct](https://www.contributor-covenant.org/version/2/1/code_of_conduct/).

## License

This project is licensed under the [Apache License 2.0](LICENSE).

## Related projects

- [Morphium](https://github.com/sboesebeck/morphium) – the underlying MongoDB ORM
- [Quarkus](https://quarkus.io) – the supersonic, subatomic Java framework
- [Quarkiverse Hub](https://github.com/quarkiverse) – community Quarkus extensions
