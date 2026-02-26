# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Initial implementation of the Quarkus Morphium extension
- `@ApplicationScoped` CDI producer for `Morphium` via `MorphiumProducer`
- Type-safe runtime configuration via `@ConfigMapping(prefix = "morphium")`:
  - `morphium.hosts` – MongoDB host list (default: `localhost:27017`)
  - `morphium.database` – target database name
  - `morphium.username` / `morphium.password` – optional credentials
  - `morphium.auth-database` – authentication database (default: `admin`)
  - `morphium.read-preference` – read preference (default: `primary`)
  - `morphium.create-indexes` – automatic index creation on startup (default: `true`)
  - `morphium.max-connections` – connection pool size (default: `250`)
  - `morphium.atlas-url` – optional MongoDB Atlas connection string (overrides `hosts`)
  - `morphium.driver-name` – Morphium driver (default: `PooledDriver`; use `InMemDriver` for tests)
  - `morphium.cache.global-valid-time` – query cache TTL in ms (default: `60000`)
  - `morphium.cache.read-cache-enabled` – enable/disable query cache (default: `true`)
- Build-time ClassGraph scan: automatic GraalVM reflection registration for all
  `@Entity` and `@Embedded` annotated classes (no manual `reflect-config.json` required)
- Graceful shutdown via `@Observes ShutdownEvent` – `Morphium.close()` called automatically
- Java 25 compatible: no `sun.*` imports, no `Unsafe` access, no `--add-opens` for internal APIs
- `InMemDriver` support for `@QuarkusTest` without a running MongoDB instance
- Quarkus 3.32.1 support
