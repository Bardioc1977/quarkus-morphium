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
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Quarkus build-time processor that automatically starts a MongoDB container
 * in dev and test mode when no explicit {@code quarkus.morphium.hosts} is configured.
 *
 * <p>Uses the Quarkus {@code owned()} Dev Services API so that the framework
 * manages the container lifecycle via the cross-classloader
 * {@code RunningDevServicesRegistry}. This ensures container reuse across
 * augmentation phases (e.g. different {@code @QuarkusTestProfile} switches)
 * without relying on static fields that can be lost with classloader changes.
 *
 * <p>Dev Services are skipped when:
 * <ul>
 *   <li>{@code quarkus.morphium.devservices.enabled=false}</li>
 *   <li>{@code quarkus.morphium.hosts} is explicitly set in {@code application.properties}</li>
 *   <li>The application runs in normal (production) mode</li>
 * </ul>
 */
public class MorphiumDevServicesProcessor {

    private static final Logger log = Logger.getLogger(MorphiumDevServicesProcessor.class);

    @BuildStep(onlyIf = IsDevServicesSupportedByLaunchMode.class)
    public DevServicesResultBuildItem startDevServices(MorphiumDevServicesBuildTimeConfig config) {

        if (!config.enabled()) {
            log.debug("Morphium Dev Services disabled via quarkus.morphium.devservices.enabled=false");
            return null;
        }

        Optional<String> explicitHosts =
                ConfigProvider.getConfig().getOptionalValue("quarkus.morphium.hosts", String.class);
        Optional<String> explicitReplicaSetName =
                ConfigProvider.getConfig().getOptionalValue("quarkus.morphium.replica-set-name", String.class);
        if (explicitHosts.isPresent() || explicitReplicaSetName.isPresent()) {
            log.debug("Morphium connection settings already configured – skipping Dev Services");
            return null;
        }

        // Use a plain String for serviceConfig instead of a record.
        // Quarkus compares serviceConfig across classloader boundaries via
        // ComparableDevServicesConfig.reflectiveEquals(), which falls back to
        // Object.equals() for objects without @ConfigMapping/@ConfigGroup interfaces.
        // Record.equals() uses instanceof, which fails across classloaders.
        // String.equals() compares char arrays and works reliably cross-classloader.
        String serviceConfig = config.imageName() + "|" + config.databaseName() + "|" + config.replicaSet();

        Map<String, Function<MongoDBStartable, String>> configProvider = new HashMap<>();
        configProvider.put("quarkus.morphium.hosts", s -> s.getHost() + ":" + s.getMappedPort());
        configProvider.put("quarkus.morphium.database", s -> config.databaseName());

        if (config.replicaSet()) {
            configProvider.put("quarkus.morphium.replica-set-name", s -> {
                String rsName = s.getReplicaSetName();
                return rsName != null ? rsName : "docker-rs";
            });
        }

        log.infof("Morphium Dev Services: requesting MongoDB %s from image '%s' (Quarkus-managed lifecycle)",
                config.replicaSet() ? "replica set" : "standalone", config.imageName());

        return DevServicesResultBuildItem.<MongoDBStartable>owned()
                .feature("morphium")
                .serviceName("morphium-mongodb")
                .description("MongoDB (" + config.imageName() + ")")
                .serviceConfig(serviceConfig)
                .startable(() -> new MongoDBStartable(config.imageName(), config.replicaSet()))
                .configProvider(configProvider)
                .build();
    }
}
