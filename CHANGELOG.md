# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- **BREAKING:** Config prefix changed from `morphium.*` to `quarkus.morphium.*` to follow
  Quarkus extension conventions. Rename all `morphium.` properties in your
  `application.properties` to `quarkus.morphium.` (e.g. `morphium.database` becomes
  `quarkus.morphium.database`). Dev Services keys (`quarkus.morphium.devservices.*`) are
  unchanged.
- LICENSE copyright updated from `Bardioc1977` to `The Quarkiverse Authors`

### Added
- Apache 2.0 copyright headers in all Java source files
- POM metadata: `<scm>`, `<licenses>`, `<developers>`, `<url>`, `<issueManagement>`
- Governance files: `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`
- GitHub templates: issue templates, PR template, `CODEOWNERS`, `dependabot.yml`
- `.editorconfig` for consistent code style
- `keywords` metadata in `quarkus-extension.yaml`
- `@MorphiumTransactional` CDI interceptor for declarative transaction management –
  automatically calls `startTransaction()` / `commitTransaction()` / `abortTransaction()`
- Transaction lifecycle CDI events (`MorphiumTransactionEvent`) with `@MorphiumTxPhase` qualifier:
  `BEFORE_COMMIT`, `AFTER_COMMIT`, `AFTER_ROLLBACK` (includes the causing exception)
- Initial implementation of the Quarkus Morphium extension
- `@ApplicationScoped` CDI producer for `Morphium` via `MorphiumProducer`
- Type-safe runtime configuration via `@ConfigMapping(prefix = "quarkus.morphium")`:
  - `quarkus.morphium.hosts` – MongoDB host list (default: `localhost:27017`)
  - `quarkus.morphium.database` – target database name
  - `quarkus.morphium.username` / `quarkus.morphium.password` – optional credentials
  - `quarkus.morphium.auth-database` – authentication database (default: `admin`)
  - `quarkus.morphium.read-preference` – read preference (default: `primary`)
  - `quarkus.morphium.create-indexes` – automatic index creation on startup (default: `true`)
  - `quarkus.morphium.max-connections` – connection pool size (default: `250`)
  - `quarkus.morphium.atlas-url` – optional MongoDB Atlas connection string (overrides `hosts`)
  - `quarkus.morphium.driver-name` – Morphium driver (default: `PooledDriver`; use `InMemDriver` for tests)
  - `quarkus.morphium.cache.global-valid-time` – query cache TTL in ms (default: `60000`)
  - `quarkus.morphium.cache.read-cache-enabled` – enable/disable query cache (default: `true`)
- Build-time ClassGraph scan: automatic GraalVM reflection registration for all
  `@Entity` and `@Embedded` annotated classes (no manual `reflect-config.json` required)
- Graceful shutdown via `@Observes ShutdownEvent` – `Morphium.close()` called automatically
- Java 25 compatible: no `sun.*` imports, no `Unsafe` access, no `--add-opens` for internal APIs
- `InMemDriver` support for `@QuarkusTest` without a running MongoDB instance
- Quarkus 3.32.1 support
- Dev Services: automatic MongoDB container start in dev and test mode via Testcontainers
  (`quarkus.morphium.devservices.*` config group; disabled when `quarkus.morphium.hosts` is set explicitly)
