package de.caluga.morphium.quarkus;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
import de.caluga.morphium.ObjectMapperImpl;
import de.caluga.morphium.driver.wire.SslHelper;
import de.caluga.morphium.objectmapping.LocalDateTimeMapper;
import java.time.LocalDateTime;
import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CDI producer for a single {@link Morphium} instance shared across the application.
 *
 * <p>Design principles:
 * <ul>
 *   <li>No {@code sun.*} or {@code jdk.internal.*} imports</li>
 *   <li>No {@link java.lang.reflect.Field#setAccessible} beyond what Morphium itself requires</li>
 *   <li>Lifecycle managed via CDI {@code @Observes} – no custom shutdown hooks</li>
 * </ul>
 */
@ApplicationScoped
public class MorphiumProducer {

    private static final Logger log = LoggerFactory.getLogger(MorphiumProducer.class);

    @Inject
    MorphiumRuntimeConfig config;

    // Kept as a field so the shutdown observer can close it.
    private volatile Morphium instance;

    @Produces
    @ApplicationScoped
    public Morphium morphium() {
        if (instance != null) {
            return instance;
        }
        synchronized (this) {
            if (instance != null) {
                return instance;
            }
            instance = buildMorphium();
        }
        return instance;
    }

    void onStop(@Observes ShutdownEvent event) {
        if (instance != null) {
            log.info("Closing Morphium connection on application shutdown");
            try {
                instance.close();
            } catch (Exception e) {
                log.warn("Error while closing Morphium", e);
            } finally {
                instance = null;
            }
        }
    }

    // ------------------------------------------------------------------
    // Internal helpers – no reflection, no Unsafe
    // ------------------------------------------------------------------

    private void configureSsl(MorphiumConfig cfg, SslConfig ssl) {
        if (!ssl.enabled()) {
            return;
        }

        cfg.setUseSSL(true);
        cfg.setSslInvalidHostNameAllowed(ssl.invalidHostnameAllowed());

        // Auth mechanism (e.g. MONGODB-X509)
        ssl.authMechanism().ifPresent(cfg::setAuthMechanism);

        // Build SSLContext from keystore / truststore if provided
        String keystorePath     = ssl.keystorePath().orElse(null);
        String keystorePassword = ssl.keystorePassword().orElse(null);
        String truststorePath     = ssl.truststorePath().orElse(null);
        String truststorePassword = ssl.truststorePassword().orElse(null);

        if (keystorePath != null || truststorePath != null) {
            try {
                javax.net.ssl.SSLContext sslContext = SslHelper.createSslContext(
                        keystorePath, keystorePassword,
                        truststorePath, truststorePassword);
                cfg.setSslContext(sslContext);
                log.debug("SSLContext configured from keystore='{}', truststore='{}'",
                        keystorePath, truststorePath);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Failed to build SSLContext from morphium.ssl configuration: " + e.getMessage(), e);
            }
        }

        // Explicit X.509 username (subject DN override)
        ssl.x509Username().ifPresent(dn -> {
            log.debug("Using explicit X.509 username (subject DN): {}", dn);
            cfg.authSettings().setMongoLogin(dn);
            // No password for X.509 – set empty to avoid SCRAM credential check
            cfg.authSettings().setMongoPassword("");
            cfg.authSettings().setMongoAuthDb("$external");
        });
    }

    private Morphium buildMorphium() {
        // Clear the static entity-class cache so ClassGraph re-scans with the current
        // ClassLoader. Required in Quarkus dev mode where hot-reload replaces the
        // QuarkusClassLoader – without this, stale class references from the previous
        // loader cause ObjectMapperImpl to silently skip all @Entity classes.
        ObjectMapperImpl.clearEntityCache();

        MorphiumConfig cfg = new MorphiumConfig();

        cfg.connectionSettings().setDatabase(config.database());
        cfg.driverSettings().setDriverName(config.driverName());
        cfg.connectionSettings().setMaxConnections(config.maxConnections());
        cfg.driverSettings().setDefaultReadPreferenceType(config.readPreference());
        if (config.createIndexes()) {
            cfg.setAutoIndexAndCappedCreationOnWrite(true);
        }

        // Host configuration
        if (config.atlasUrl().isPresent()) {
            // Use ClusterSettings.setAtlasUrl() for mongodb+srv:// connection strings.
            // Morphium resolves the SRV record automatically in initializeAndConnect().
            cfg.clusterSettings().setAtlasUrl(config.atlasUrl().get());
        } else {
            for (String host : config.hosts()) {
                String trimmed = host.trim();
                if (!trimmed.isEmpty()) {
                    cfg.clusterSettings().addHostToSeed(trimmed);
                }
            }
        }

        // Credentials
        if (config.username().isPresent() && config.password().isPresent()) {
            cfg.authSettings().setMongoLogin(config.username().get());
            cfg.authSettings().setMongoPassword(config.password().get());
            cfg.authSettings().setMongoAuthDb(config.authDatabase());
        }

        // Cache settings
        cfg.cacheSettings().setGlobalCacheValidTime((int) config.cache().globalValidTime());
        cfg.cacheSettings().setReadCacheEnabled(config.cache().readCacheEnabled());

        // TLS / X.509 settings
        configureSsl(cfg, config.ssl());

        log.info("Creating Morphium connection to database '{}' (driver: {}, ssl: {}, authMechanism: {}, localDateTimeAsBsonDate: {})",
            config.database(), config.driverName(),
            config.ssl().enabled(),
            config.ssl().authMechanism().orElse("SCRAM (default)"),
            config.localDateTime().useBsonDate());

        Morphium m = new Morphium(cfg);

        // Override the default LocalDateTimeMapper with the configured format.
        // useBsonDate=true  → ISODate (native MongoDB dates, compatible with Morphia data)
        // useBsonDate=false → Map{sec, n} (legacy Morphium format)
        m.getMapper().registerCustomMapperFor(LocalDateTime.class,
                new LocalDateTimeMapper(config.localDateTime().useBsonDate()));

        return m;
    }
}
