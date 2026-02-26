package de.caluga.morphium.quarkus.deployment;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Build-time configuration for Morphium Dev Services.
 *
 * <p>Dev Services automatically start a MongoDB container in dev and test mode
 * when no explicit {@code morphium.hosts} is configured.
 *
 * <p>Example – disable Dev Services (use an external MongoDB instead):
 * <pre>{@code
 * quarkus.morphium.devservices.enabled=false
 * morphium.hosts=my-mongo:27017
 * morphium.database=mydb
 * }</pre>
 */
@ConfigMapping(prefix = "quarkus.morphium.devservices")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface MorphiumDevServicesBuildTimeConfig {

    /**
     * Whether Dev Services are enabled.
     * Set to {@code false} to use an external MongoDB and suppress container startup.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Docker image name for the MongoDB container.
     * Defaults to {@code mongo:8} (latest MongoDB 8.x).
     */
    @WithDefault("mongo:8")
    String imageName();

    /**
     * Database name injected as {@code morphium.database} when Dev Services start.
     * Override in {@code application.properties} if a different name is needed.
     */
    @WithDefault("morphium-dev")
    String databaseName();
}
