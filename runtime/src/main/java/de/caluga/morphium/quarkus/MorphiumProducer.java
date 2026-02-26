package de.caluga.morphium.quarkus;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
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
            }
        }
    }

    // ------------------------------------------------------------------
    // Internal helpers – no reflection, no Unsafe
    // ------------------------------------------------------------------

    private Morphium buildMorphium() {
        MorphiumConfig cfg = new MorphiumConfig();

        cfg.setDatabase(config.database());
        cfg.setDriverName(config.driverName());
        cfg.setMaxConnections(config.maxConnections());

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

        log.info("Creating Morphium connection to database '{}' (hosts: {})",
            config.database(), config.hosts());

        return new Morphium(cfg);
    }
}
