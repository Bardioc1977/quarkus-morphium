package de.caluga.morphium.quarkus;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
import de.caluga.morphium.driver.wire.SslHelper;
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
            cfg.setMongoLogin(dn);
            // No password for X.509 – set empty to avoid SCRAM credential check
            cfg.setMongoPassword("");
            cfg.setMongoAuthDb("$external");
        });
    }

    private Morphium buildMorphium() {
        MorphiumConfig cfg = new MorphiumConfig();

        cfg.setDatabase(config.database());
        cfg.setDriverName(config.driverName());
        cfg.setMaxConnections(config.maxConnections());
        cfg.setDefaultReadPreferenceType(config.readPreference());
        if (config.createIndexes()) {
            cfg.setAutoIndexAndCappedCreationOnWrite(true);
        }

        // Host configuration
        if (config.atlasUrl().isPresent()) {
            cfg.addHostToSeed(config.atlasUrl().get());
        } else {
            for (String host : config.hosts()) {
                String trimmed = host.trim();
                if (!trimmed.isEmpty()) {
                    cfg.addHostToSeed(trimmed);
                }
            }
        }

        // Credentials
        if (config.username().isPresent() && config.password().isPresent()) {
            cfg.setMongoLogin(config.username().get());
            cfg.setMongoPassword(config.password().get());
            cfg.setMongoAuthDb(config.authDatabase());
        }

        // Cache settings
        cfg.setGlobalCacheValidTime((int) config.cache().globalValidTime());
        cfg.setReadCacheEnabled(config.cache().readCacheEnabled());

        // TLS / X.509 settings
        configureSsl(cfg, config.ssl());

        log.info("Creating Morphium connection to database '{}' (driver: {}, ssl: {}, authMechanism: {})",
            config.database(), config.driverName(),
            config.ssl().enabled(),
            config.ssl().authMechanism().orElse("SCRAM (default)"));

        return new Morphium(cfg);
    }
}
