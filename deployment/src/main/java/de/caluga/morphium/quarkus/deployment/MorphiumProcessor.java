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

import de.caluga.morphium.annotations.Embedded;
import de.caluga.morphium.annotations.Entity;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.smallrye.health.deployment.spi.HealthBuildItem;
import de.caluga.morphium.quarkus.MorphiumBlockingCallDetector;
import de.caluga.morphium.quarkus.MorphiumProducer;
import de.caluga.morphium.quarkus.transaction.MorphiumTransactionalInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quarkus build-time processor for the Morphium extension.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Register the {@code "morphium"} feature so it appears in the Quarkus banner.</li>
 *   <li>Make the CDI producer bean available to the application.</li>
 *   <li>Register all classes annotated with {@link Entity} or {@link Embedded}
 *       for GraalVM reflection so that Morphium's ObjectMapper can serialise and
 *       deserialise them in a native image without requiring {@code reflect-config.json}.</li>
 * </ol>
 *
 * <p>This class uses only standard Quarkus build-item APIs and ClassGraph for
 * classpath scanning – no {@code sun.*} imports, no {@code Unsafe} access.
 */
public class MorphiumProcessor {

    private static final Logger log = LoggerFactory.getLogger(MorphiumProcessor.class);

    // ------------------------------------------------------------------
    // Feature registration
    // ------------------------------------------------------------------

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(MorphiumFeature.FEATURE_NAME);
    }

    // ------------------------------------------------------------------
    // CDI bean registration
    // ------------------------------------------------------------------

    @BuildStep
    AdditionalBeanBuildItem registerBeans() {
        // Only MorphiumProducer is a CDI bean.
        // MorphiumRuntimeConfig / CacheConfig are @ConfigMapping interfaces and are
        // registered automatically by the SmallRye Config Quarkus extension.
        // MorphiumRecorder is a @Recorder (build-time only) and must not appear here.
        return AdditionalBeanBuildItem.builder()
            .addBeanClasses(
                MorphiumProducer.class,
                MorphiumTransactionalInterceptor.class,
                MorphiumBlockingCallDetector.class)
            .setUnremovable()
            .build();
    }

    // ------------------------------------------------------------------
    // Health check registration
    // ------------------------------------------------------------------

    @BuildStep
    HealthBuildItem addLivenessCheck(MorphiumHealthBuildTimeConfig config) {
        return new HealthBuildItem(
                "de.caluga.morphium.quarkus.health.MorphiumLivenessCheck",
                config.enabled());
    }

    @BuildStep
    HealthBuildItem addReadinessCheck(MorphiumHealthBuildTimeConfig config) {
        return new HealthBuildItem(
                "de.caluga.morphium.quarkus.health.MorphiumReadinessCheck",
                config.enabled());
    }

    @BuildStep
    HealthBuildItem addStartupCheck(MorphiumHealthBuildTimeConfig config) {
        return new HealthBuildItem(
                "de.caluga.morphium.quarkus.health.MorphiumStartupCheck",
                config.enabled());
    }

    // ------------------------------------------------------------------
    // GraalVM native image: reflection registration for @Entity / @Embedded
    // ------------------------------------------------------------------

    @BuildStep
    void registerEntitiesForReflection(BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {
        try (ScanResult scan = new ClassGraph()
                .enableClassInfo()
                .enableAnnotationInfo()
                .scan()) {

            for (ClassInfo ci : scan.getClassesWithAnnotation(Entity.class.getName())) {
                registerClass(ci.getName(), reflectiveClasses);
            }
            for (ClassInfo ci : scan.getClassesWithAnnotation(Embedded.class.getName())) {
                registerClass(ci.getName(), reflectiveClasses);
            }
        } catch (Exception e) {
            log.warn("Morphium: classpath scan for @Entity / @Embedded failed – "
                + "native image may require manual reflect-config.json entries", e);
        }
    }

    private void registerClass(String className,
                               BuildProducer<ReflectiveClassBuildItem> out) {
        log.debug("Morphium: registering {} for reflection (native image)", className);
        out.produce(ReflectiveClassBuildItem.builder(className)
            .constructors(true)
            .methods(true)
            .fields(true)
            .build());
    }
}
