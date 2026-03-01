# Release Log

This file tracks releases of the Quarkus Morphium Extension.

For a detailed list of all changes see [CHANGELOG.md](CHANGELOG.md).

## Upcoming: 1.0.0

**Target:** First public release

**Status:** Pre-release (SNAPSHOT)

### Highlights

- Full Quarkus CDI integration for Morphium MongoDB ORM
- Declarative transactions via `@MorphiumTransactional` with CDI lifecycle events
- Type-safe configuration under `quarkus.morphium.*` prefix (~25 properties)
- SSL/TLS and X.509 client-certificate authentication
- MicroProfile Health probes (liveness, readiness, startup)
- Dev Services with automatic MongoDB container (standalone or replica set)
- Dev UI card showing connection info at `/q/dev-ui/`
- Blocking call detection for Vert.x event-loop safety
- GraalVM native image support (automatic reflection registration)
- InMemDriver test profile for fast, container-free tests
- Comprehensive Antora documentation with GitHub Pages deployment

### Compatibility

| Dependency | Version |
|---|---|
| Java | 21+ |
| Quarkus | 3.32.1 |
| Morphium | 6.2.0-SNAPSHOT |

### Distribution

- **GitHub Packages** (interim): `io.quarkiverse.morphium:quarkus-morphium:1.0.0-SNAPSHOT`
- **Maven Central** (planned): via Quarkiverse release pipeline

### Migration

This is the initial release. If upgrading from an earlier SNAPSHOT, note:

- Config prefix changed from `morphium.*` to `quarkus.morphium.*`
- All `morphium.` properties in `application.properties` must be renamed to `quarkus.morphium.*`
- Dev Services keys (`quarkus.morphium.devservices.*`) are unchanged
