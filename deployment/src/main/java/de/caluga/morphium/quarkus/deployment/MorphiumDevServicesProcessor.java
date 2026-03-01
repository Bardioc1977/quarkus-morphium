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
package de.caluga.morphium.quarkus.deployment;

import io.quarkus.deployment.IsDevServicesSupportedByLaunchMode;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.Optional;

/**
 * Quarkus build-time processor that automatically starts a MongoDB container
 * in dev and test mode when no explicit {@code quarkus.morphium.hosts} is configured.
 *
 * <p>The container is reused across live reloads (static field) and stopped
 * automatically when Quarkus shuts down (JVM shutdown hook registered on first start).
 *
 * <p>Dev Services are skipped when:
 * <ul>
 *   <li>{@code quarkus.morphium.devservices.enabled=false}</li>
 *   <li>{@code quarkus.morphium.hosts} is explicitly set in {@code application.properties}</li>
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

    @BuildStep(onlyIf = IsDevServicesSupportedByLaunchMode.class)
    public DevServicesResultBuildItem startDevServices(MorphiumDevServicesBuildTimeConfig config) {

        if (!config.enabled()) {
            log.debug("Morphium Dev Services disabled via quarkus.morphium.devservices.enabled=false");
            stopContainerIfRunning();
            return null;
        }

        // If quarkus.morphium.hosts is explicitly set in application config, respect it
        Optional<String> explicitHosts =
                ConfigProvider.getConfig().getOptionalValue("quarkus.morphium.hosts", String.class);
        if (explicitHosts.isPresent()) {
            log.debug("quarkus.morphium.hosts={} – skipping Dev Services", explicitHosts.get());
            stopContainerIfRunning();
            return null;
        }

        // Reuse a container that is still running (live reload scenario),
        // but only when the replica-set mode matches what is currently configured.
        // If the mode changed (e.g. standalone → replica-set), restart the container.
        boolean wantsReplicaSet = config.replicaSet();
        boolean currentIsReplicaSet = devContainer instanceof MongoDBContainer;
        if (devContainer != null && devContainer.isRunning() && wantsReplicaSet == currentIsReplicaSet) {
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

        @SuppressWarnings("resource")   // lifecycle managed via shutdown hook below
        final GenericContainer<?> container;

        if (config.replicaSet()) {
            log.info("Morphium Dev Services: starting MongoDB single-node replica set from image '{}' "
                    + "(transactions enabled)", image);
            // MongoDBContainer handles --replSet, rs.initiate() and PRIMARY wait automatically.
            container = new MongoDBContainer(DockerImageName.parse(image));
        } else {
            log.info("Morphium Dev Services: starting MongoDB standalone container from image '{}'", image);
            container = new GenericContainer<>(DockerImageName.parse(image))
                    .withExposedPorts(MONGO_PORT)
                    .waitingFor(Wait.forLogMessage(".*Waiting for connections.*\n", 1));
        }

        try {
            container.start();
        } catch (Exception e) {
            try {
                container.close();
            } catch (Exception suppressed) {
                e.addSuppressed(suppressed);
            }
            log.warn("Morphium Dev Services: failed to start MongoDB container – "
                    + "falling back to configured quarkus.morphium.hosts (if any). Cause: {}", e.getMessage());
            return null;
        }
        devContainer = container;

        int mappedPort = container.getMappedPort(MONGO_PORT);
        log.info("Morphium Dev Services: MongoDB {} ready at localhost:{} (container {})",
                config.replicaSet() ? "replica set" : "standalone",
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
        return DevServicesResultBuildItem.discovered()
                .feature("morphium")
                .containerId(container.getContainerId())
                .config(Map.of(
                        "quarkus.morphium.hosts",    "localhost:" + port,
                        "quarkus.morphium.database", config.databaseName()
                ))
                .build();
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
