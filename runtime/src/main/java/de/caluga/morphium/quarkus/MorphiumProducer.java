/*
 * Copyright 2025 The Quarkiverse Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.caluga.morphium.quarkus;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
import de.caluga.morphium.ObjectMapperImpl;
import de.caluga.morphium.config.CollectionCheckSettings;
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
                        "Failed to build SSLContext from quarkus.morphium.ssl configuration: " + e.getMessage(), e);
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

        // Map the quarkus.morphium.index-check enum to Morphium's CollectionCheckSettings
        switch (config.indexCheck()) {
            case CREATE_ON_STARTUP:
                cfg.collectionCheckSettings().setIndexCheck(CollectionCheckSettings.IndexCheck.CREATE_ON_STARTUP);
                break;
            case WARN_ON_STARTUP:
                cfg.collectionCheckSettings().setIndexCheck(CollectionCheckSettings.IndexCheck.WARN_ON_STARTUP);
                break;
            case CREATE_ON_WRITE_NEW_COL:
                cfg.setAutoIndexAndCappedCreationOnWrite(true);
                break;
            case NO_CHECK:
                cfg.collectionCheckSettings().setIndexCheck(CollectionCheckSettings.IndexCheck.NO_CHECK);
                break;
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

        // Replica set name (required for transactions)
        if (config.replicaSetName().isPresent()) {
            cfg.clusterSettings().setRequiredReplicaSetName(config.replicaSetName().get());
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

        log.info("Quarkus Morphium Extension v{} (Morphium {}, Jakarta Data {})",
            MorphiumVersion.extensionVersion(), MorphiumVersion.morphiumVersion(),
            MorphiumVersion.jakartaDataVersion());
        log.info("Creating Morphium connection to database '{}' (hosts: {}, driver: {}, replicaSetName: {}, ssl: {})",
            config.database(), config.hosts(), config.driverName(),
            config.replicaSetName().orElse("(none)"),
            config.ssl().enabled());

        Morphium m = connectWithRetry(cfg);

        // Defensive: ensure the driver knows it's a replica set when a RS name is configured.
        // PooledDriver < 6.2.1 only checked host-seed count, missing single-node replica sets.
        if (config.replicaSetName().isPresent() && !m.getDriver().isReplicaSet()) {
            log.debug("Forcing replicaSet=true on driver (single-node replica set workaround)");
            m.getDriver().setReplicaSet(true);
        }

        // Override the default LocalDateTimeMapper with the configured format.
        // useBsonDate=true  → ISODate (native MongoDB dates, compatible with Morphia data)
        // useBsonDate=false → Map{sec, n} (legacy Morphium format)
        m.getMapper().registerCustomMapperFor(LocalDateTime.class,
                new LocalDateTimeMapper(config.localDateTime().useBsonDate()));

        // Morphium's built-in index creation uses ClassGraph which does not work
        // with Quarkus's classloader. Use the entity classes discovered at build time
        // and explicitly ensure their indexes — but only when configured to do so.
        if (config.indexCheck() == MorphiumRuntimeConfig.IndexCheckMode.CREATE_ON_STARTUP) {
            ensureIndices(m);
        }

        return m;
    }

    /**
     * Creates a Morphium instance with retry logic. In containerized CI environments
     * (e.g. Docker-in-Docker), the MongoDB replica set primary may not be immediately
     * reachable after the container reports ready. This method retries the connection
     * with exponential backoff to handle transient startup delays.
     */
    private Morphium connectWithRetry(MorphiumConfig cfg) {
        int maxAttempts = config.connectRetries();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return new Morphium(cfg);
            } catch (Exception e) {
                boolean isTransient = isTransientConnectionError(e);
                if (!isTransient || attempt == maxAttempts) {
                    throw e;
                }
                long delayMs = attempt * 2000L;
                log.warn("Morphium connection attempt {}/{} failed: {}. Retrying in {}ms...",
                        attempt, maxAttempts, e.getMessage(), delayMs);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while retrying Morphium connection", ie);
                }
            }
        }
        throw new IllegalStateException("Unreachable");
    }

    private static boolean isTransientConnectionError(Throwable t) {
        while (t != null) {
            String msg = t.getMessage();
            if (msg != null && (msg.contains("No primary node found")
                    || msg.contains("not connected yet"))) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private void ensureIndices(Morphium m) {
        for (String className : MorphiumRecorder.getEntityClassNames()) {
            try {
                Class<?> entityClass = Thread.currentThread().getContextClassLoader().loadClass(className);
                m.ensureIndicesFor(entityClass);
                log.debug("Ensured indexes for {}", className);
            } catch (ClassNotFoundException e) {
                log.warn("Could not load entity class for index creation: {}", className);
            } catch (Exception e) {
                log.warn("Failed to ensure indexes for {}: {}", className, e.getMessage(), e);
            }
        }
    }
}
