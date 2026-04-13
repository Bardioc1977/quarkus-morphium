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

import de.caluga.morphium.AnnotationAndReflectionHelper;
import de.caluga.morphium.ClassGraphCache;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
import de.caluga.morphium.ObjectMapperImpl;
import de.caluga.morphium.annotations.Capped;
import de.caluga.morphium.annotations.Driver;
import de.caluga.morphium.annotations.Embedded;
import de.caluga.morphium.annotations.Entity;
import de.caluga.morphium.annotations.Messaging;
import de.caluga.morphium.config.CollectionCheckSettings;
import de.caluga.morphium.driver.wire.SslHelper;
import de.caluga.morphium.objectmapping.LocalDateTimeMapper;
import io.quarkus.runtime.ImageMode;
import io.quarkus.tls.TlsConfiguration;
import io.quarkus.tls.TlsConfigurationRegistry;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
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

    @Inject
    Instance<TlsConfigurationRegistry> tlsRegistryInstance;

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

        // Build SSLContext — explicit keystore/truststore paths take precedence,
        // then fall back to the Quarkus TLS registry (quarkus.tls.* properties).
        String keystorePath     = ssl.keystorePath().orElse(null);
        String keystorePassword = ssl.keystorePassword().orElse(null);
        String truststorePath     = ssl.truststorePath().orElse(null);
        String truststorePassword = ssl.truststorePassword().orElse(null);

        if (keystorePath != null || truststorePath != null) {
            // Explicit extension-specific paths — existing behavior
            try {
                SSLContext sslContext = SslHelper.createSslContext(
                        keystorePath, keystorePassword,
                        truststorePath, truststorePassword);
                cfg.setSslContext(sslContext);
                log.debug("SSLContext configured from keystore='{}', truststore='{}'",
                        keystorePath, truststorePath);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Failed to build SSLContext from quarkus.morphium.ssl configuration: " + e.getMessage(), e);
            }
        } else {
            // No explicit paths — try Quarkus TLS registry (quarkus.tls.* properties)
            configureSslFromTlsRegistry(cfg, ssl);
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

    /**
     * Attempts to configure the SSLContext from the Quarkus TLS registry.
     * This supports the standard {@code quarkus.tls.*} properties written by
     * runtime scripts (e.g. {@code run-quarkus-native.sh} for KEYSTORE_REGISTER
     * compatibility in native images).
     *
     * <p>Resolution order:
     * <ol>
     *   <li>If {@code quarkus.morphium.ssl.tls-configuration-name} is set, look up
     *       that named (or default) TLS configuration.</li>
     *   <li>Otherwise, try the unnamed default TLS configuration.</li>
     * </ol>
     */
    private void configureSslFromTlsRegistry(MorphiumConfig cfg, SslConfig ssl) {
        if (!tlsRegistryInstance.isResolvable()) {
            log.debug("Quarkus TLS registry not available — no SSLContext configured");
            return;
        }

        TlsConfigurationRegistry tlsRegistry = tlsRegistryInstance.get();
        Optional<TlsConfiguration> tlsConfig;

        if (ssl.tlsConfigurationName().isPresent()) {
            // Explicit named TLS configuration requested
            String name = ssl.tlsConfigurationName().get();
            if ("<default>".equals(name)) {
                tlsConfig = tlsRegistry.getDefault();
            } else {
                tlsConfig = tlsRegistry.get(name);
            }
            if (tlsConfig.isEmpty()) {
                throw new IllegalStateException(
                        "Quarkus TLS configuration '" + name + "' not found. "
                        + "Ensure quarkus.tls."
                        + ("<default>".equals(name) ? "" : name + ".")
                        + "key-store.* / trust-store.* is configured.");
            }
        } else {
            // No explicit name — try the default TLS configuration
            tlsConfig = tlsRegistry.getDefault();
        }

        if (tlsConfig.isPresent()) {
            try {
                SSLContext sslContext = tlsConfig.get().createSSLContext();
                cfg.setSslContext(sslContext);
                String configName = tlsConfig.get().getName() != null
                        ? tlsConfig.get().getName() : "<default>";
                log.info("SSLContext configured from Quarkus TLS registry (configuration: '{}')",
                        configName);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Failed to create SSLContext from Quarkus TLS registry: " + e.getMessage(), e);
            }
        } else {
            log.debug("No default Quarkus TLS configuration found — SSLContext not configured");
        }
    }

    private Morphium buildMorphium() {
        // Clear static caches and pre-register entities for the current ClassLoader.
        // This is essential for Quarkus dev-mode hot-reload where the QuarkusClassLoader
        // is replaced — without this, stale class references from the previous loader cause
        // ObjectMapperImpl/AnnotationAndReflectionHelper to silently skip all @Entity classes.
        // In production mode this is a harmless one-time init (clear of empty state + register).
        ObjectMapperImpl.clearEntityCache();
        AnnotationAndReflectionHelper.clearTypeIdCache();
        var entityNames = MorphiumRecorder.getMappedClassNames();
        if (!entityNames.isEmpty()) {
            AnnotationAndReflectionHelper.registerTypeIds(buildTypeIdMap(entityNames));
        }

        // Pre-populate ClassGraphCache with build-time discovered @Driver, @Messaging, and
        // @Capped classes. In GraalVM native mode there is no live classpath, so ClassGraph
        // finds nothing; the preRegister() call puts the entries into the cache map before
        // Morphium's constructor calls getClassesWithAnnotation(), causing the
        // computeIfAbsent to find the pre-populated list and skip the scan entirely.
        // Even an empty list is intentional for @Capped — it prevents checkCapped() from
        // falling through to a live ClassGraph scan.
        var driverNames = MorphiumRecorder.getDriverClassNames();
        if (driverNames.isEmpty()) {
            log.warn("Morphium: no @Driver classes were discovered at build time — "
                    + "the configured driver '{}' may not be found and Morphium may fall back "
                    + "to SingleMongoConnectDriver", config.driverName());
        }
        ClassGraphCache.preRegister(Driver.class.getName(), driverNames);

        var messagingNames = MorphiumRecorder.getMessagingClassNames();
        ClassGraphCache.preRegister(Messaging.class.getName(), messagingNames);

        var cappedNames = MorphiumRecorder.getCappedClassNames();
        ClassGraphCache.preRegister(Capped.class.getName(), cappedNames);

        // Pre-register @Entity and @Embedded classes so ObjectMapperImpl can initialize
        // without triggering a live ClassGraph scan (which fails in native mode).
        // Unlike @Driver/@Messaging/@Capped (looked up by Morphium's constructor),
        // @Entity is looked up by ObjectMapperImpl.<init> and must also be pre-populated.
        var entityOnlyNames = MorphiumRecorder.getEntityClassNames();
        ClassGraphCache.preRegister(Entity.class.getName(), entityOnlyNames);

        var embeddedOnlyNames = MorphiumRecorder.getEmbeddedClassNames();
        ClassGraphCache.preRegister(Embedded.class.getName(), embeddedOnlyNames);

        MorphiumConfig cfg = new MorphiumConfig();

        cfg.connectionSettings().setDatabase(config.database());
        cfg.driverSettings().setDriverName(config.driverName());
        cfg.connectionSettings().setMaxConnections(config.maxConnections());
        cfg.connectionSettings().setMaxWaitTime(config.maxWaitTime());
        cfg.connectionSettings().setDefaultQueryTimeoutMS(config.defaultQueryTimeoutMs());
        cfg.driverSettings().setDefaultReadPreferenceType(config.readPreference());

        // Morphium's internal checkIndices() uses ClassGraph at startup.
        // In Quarkus, we handle index creation explicitly via ensureIndices() using the
        // build-time discovered entity list — so always disable Morphium's internal check
        // to avoid redundant index creation (Morphium + Producer would both call ensureIndicesFor).
        // WARN_ON_STARTUP calls checkIndices() → ClassGraphCache.getClassInfoWithAnnotation()
        // which bypasses the preRegister cache and triggers a live ClassGraph scan.
        // In native mode that scan crashes because there is no live classpath — so
        // WARN_ON_STARTUP must be downgraded to NO_CHECK in native images.
        MorphiumRuntimeConfig.IndexCheckMode effectiveIndexCheck = config.indexCheck();
        if (effectiveIndexCheck == MorphiumRuntimeConfig.IndexCheckMode.WARN_ON_STARTUP
                && ImageMode.current() == ImageMode.NATIVE_RUN) {
            log.warn("Morphium: indexCheck=WARN_ON_STARTUP is not supported in native images "
                    + "(checkIndices() calls ClassGraph directly, bypassing the preRegister cache). "
                    + "Downgrading to NO_CHECK for this native run.");
            effectiveIndexCheck = MorphiumRuntimeConfig.IndexCheckMode.NO_CHECK;
        }
        switch (effectiveIndexCheck) {
            case CREATE_ON_STARTUP:
                // Disable Morphium-internal creation — Producer.ensureIndices() handles it
                cfg.collectionCheckSettings().setIndexCheck(CollectionCheckSettings.IndexCheck.NO_CHECK);
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

        log.info("Morphium connected (replicaSet: {}, replicaSetName: {})",
            m.getDriver().isReplicaSet(),
            m.getDriver().getReplicaSetName() != null ? m.getDriver().getReplicaSetName() : "(none)");

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
     * with linear backoff (2s, 4s, 6s, ...) to handle transient startup delays.
     */
    private Morphium connectWithRetry(MorphiumConfig cfg) {
        int maxAttempts = Math.max(1, config.connectRetries());
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

    /**
     * Builds a typeId→FQCN map from the entity class names discovered at build time.
     * Loads each class, reads its @Entity/@Embedded annotation, and extracts the typeId.
     */
    private Map<String, String> buildTypeIdMap(List<String> classNames) {
        Map<String, String> typeIds = new HashMap<>();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        for (String cn : classNames) {
            try {
                Class<?> cls = Class.forName(cn, false, cl);
                Entity entity = cls.getAnnotation(Entity.class);
                if (entity != null) {
                    if (!".".equals(entity.typeId())) {
                        typeIds.put(entity.typeId(), cn);
                    }
                    typeIds.put(cn, cn);
                }
                Embedded embedded = cls.getAnnotation(Embedded.class);
                if (embedded != null) {
                    if (!".".equals(embedded.typeId())) {
                        typeIds.put(embedded.typeId(), cn);
                    }
                    typeIds.put(cn, cn);
                }
            } catch (ClassNotFoundException e) {
                log.warn("Could not load entity class for type ID registration: {}", cn);
            }
        }
        return typeIds;
    }

    /**
     * Ensures MongoDB indexes for all {@code @Entity} classes discovered at build time.
     *
     * <p><b>Important:</b> This must iterate only {@code @Entity} classes, not the combined
     * {@code @Entity}+{@code @Embedded} list from {@link MorphiumRecorder#getMappedClassNames()}.
     * {@code Morphium.ensureIndicesFor()} calls {@code ObjectMapperImpl.getCollectionName()},
     * which throws {@code IllegalArgumentException} for {@code @Embedded}-only classes
     * (they have no collection name). Using {@link MorphiumRecorder#getEntityClassNames()} avoids this.
     */
    private void ensureIndices(Morphium m) {
        for (String className : MorphiumRecorder.getEntityClassNames()) {
            try {
                Class<?> entityClass = Thread.currentThread().getContextClassLoader().loadClass(className);
                m.ensureIndicesFor(entityClass);
                log.debug("Ensured indexes for {}", className);
            } catch (ClassNotFoundException e) {
                log.warn("Could not load entity class for index creation: {}", className);
            } catch (Exception e) {
                log.warn("Failed to ensure indexes for entity class {}: {}", className, e.getMessage(), e);
            }
        }
    }
}
