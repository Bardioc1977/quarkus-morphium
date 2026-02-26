package de.caluga.morphium.quarkus.deployment;

import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.Optional;

/**
 * Quarkus build-time processor that automatically starts a MongoDB container
 * in dev and test mode when no explicit {@code morphium.hosts} is configured.
 *
 * <p>The container is reused across live reloads (static field) and stopped
 * automatically when Quarkus shuts down (JVM shutdown hook registered on first start).
 *
 * <p>Dev Services are skipped when:
 * <ul>
 *   <li>{@code quarkus.morphium.devservices.enabled=false}</li>
 *   <li>{@code morphium.hosts} is explicitly set in {@code application.properties}</li>
 *   <li>The application runs in normal (production) mode ({@code @BuildStep(onlyIfNot = IsNormal.class)})</li>
 * </ul>
 */
public class MorphiumDevServicesProcessor {

    private static final Logger log = LoggerFactory.getLogger(MorphiumDevServicesProcessor.class);
    private static final int MONGO_PORT = 27017;

    /**
     * Shared container instance – reused across Quarkus live reloads in dev mode.
     * Guarded by the single-threaded augmentation lifecycle; {@code volatile} ensures
     * visibility after a classloader reload.
     */
    private static volatile GenericContainer<?> devContainer;

    // ------------------------------------------------------------------
    // Build step
    // ------------------------------------------------------------------

    @BuildStep(onlyIfNot = IsNormal.class)
    public DevServicesResultBuildItem startDevServices(MorphiumDevServicesBuildTimeConfig config) {

        if (!config.enabled()) {
            log.debug("Morphium Dev Services disabled via quarkus.morphium.devservices.enabled=false");
            stopContainerIfRunning();
            return null;
        }

        // If morphium.hosts is explicitly set in application config, respect it
        Optional<String> explicitHosts =
                ConfigProvider.getConfig().getOptionalValue("morphium.hosts", String.class);
        if (explicitHosts.isPresent()) {
            log.debug("morphium.hosts={} – skipping Dev Services", explicitHosts.get());
            stopContainerIfRunning();
            return null;
        }

        // Reuse a container that is still running (live reload scenario)
        if (devContainer != null && devContainer.isRunning()) {
            log.debug("Reusing existing Morphium Dev Services container ({})",
                    devContainer.getContainerId());
            return buildResult(devContainer, config);
        }

        return startNewContainer(config);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private static DevServicesResultBuildItem startNewContainer(
            MorphiumDevServicesBuildTimeConfig config) {

        String image = config.imageName();
        log.info("Morphium Dev Services: starting MongoDB container from image '{}'", image);

        @SuppressWarnings("resource")   // lifecycle managed via shutdown hook below
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse(image))
                .withExposedPorts(MONGO_PORT)
                .waitingFor(Wait.forLogMessage(".*Waiting for connections.*\\n", 1));

        try {
            container.start();
        } catch (Exception e) {
            try {
                container.close();
            } catch (Exception suppressed) {
                e.addSuppressed(suppressed);
            }
            log.warn("Morphium Dev Services: failed to start MongoDB container – "
                    + "falling back to configured morphium.hosts (if any). Cause: {}", e.getMessage());
            return null;
        }
        devContainer = container;

        int mappedPort = container.getMappedPort(MONGO_PORT);
        log.info("Morphium Dev Services: MongoDB ready at localhost:{} (container {})",
                mappedPort, container.getContainerId());

        // Ensure the container is stopped on JVM exit (test runner, dev mode Ctrl-C, etc.)
        Runtime.getRuntime().addShutdownHook(
                new Thread(MorphiumDevServicesProcessor::stopContainerIfRunning,
                        "morphium-devservices-shutdown"));

        return buildResult(container, config);
    }

    private static DevServicesResultBuildItem buildResult(
            GenericContainer<?> container, MorphiumDevServicesBuildTimeConfig config) {

        int port = container.getMappedPort(MONGO_PORT);
        return new DevServicesResultBuildItem(
                "morphium",
                container.getContainerId(),
                Map.of(
                        "morphium.hosts",    "localhost:" + port,
                        "morphium.database", config.databaseName()
                ));
    }

    private static void stopContainerIfRunning() {
        GenericContainer<?> c = devContainer;
        if (c != null && c.isRunning()) {
            log.info("Morphium Dev Services: stopping MongoDB container");
            try {
                c.stop();
            } catch (Exception ex) {
                log.warn("Failed to stop Morphium Dev Services container", ex);
            } finally {
                devContainer = null;
            }
        }
    }
}
