package de.caluga.morphium.quarkus;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;
import java.util.Optional;

/**
 * Type-safe runtime configuration for the Morphium extension.
 *
 * <p>All properties are resolved from {@code application.properties} at
 * startup – no reflection, no Unsafe access, purely CDI/SmallRye Config.
 *
 * <p>Example {@code application.properties}:
 * <pre>{@code
 * morphium.database=my-app-db
 * morphium.hosts=mongo1:27017,mongo2:27017
 * morphium.username=admin
 * morphium.password=secret
 * morphium.max-connections=250
 * }</pre>
 */
@ConfigMapping(prefix = "morphium")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface MorphiumRuntimeConfig {

    /**
     * MongoDB host list in {@code host:port} format.
     * Multiple hosts are separated by commas in application.properties.
     */
    @WithDefault("localhost:27017")
    List<String> hosts();

    /** MongoDB database name. */
    String database();

    /** MongoDB username (optional). */
    Optional<String> username();

    /** MongoDB password (optional). */
    Optional<String> password();

    /** Authentication database, defaults to {@code admin}. */
    @WithDefault("admin")
    String authDatabase();

    /**
     * Read preference for MongoDB queries.
     * Accepted values: {@code primary}, {@code primaryPreferred},
     * {@code secondary}, {@code secondaryPreferred}, {@code nearest}.
     */
    @WithDefault("primary")
    String readPreference();

    /** Whether Morphium should automatically create / verify indexes on startup. */
    @WithDefault("true")
    boolean createIndexes();

    /** Maximum number of MongoDB connections in the pool. */
    @WithDefault("250")
    int maxConnections();

    /**
     * Optional MongoDB Atlas connection string ({@code mongodb+srv://...}).
     * When present this overrides {@link #hosts()}.
     */
    Optional<String> atlasUrl();

    /**
     * Morphium driver name. Defaults to {@code PooledDriver}.
     * Use {@code InMemDriver} for tests (no MongoDB required).
     */
    @WithDefault("PooledDriver")
    String driverName();

    /** Nested cache configuration. */
    CacheConfig cache();

    /** Nested TLS / X.509 configuration. */
    SslConfig ssl();
}
