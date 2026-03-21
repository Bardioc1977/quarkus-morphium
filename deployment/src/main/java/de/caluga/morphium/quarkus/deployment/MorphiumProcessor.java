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
import de.caluga.morphium.quarkus.MorphiumRecorder;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.smallrye.health.deployment.spi.HealthBuildItem;
import de.caluga.morphium.quarkus.MorphiumBlockingCallDetector;
import de.caluga.morphium.quarkus.MorphiumProducer;
import de.caluga.morphium.quarkus.transaction.MorphiumTransactionalInterceptor;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;

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

    private static final Logger log = Logger.getLogger(MorphiumProcessor.class);

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
        // Register runtime CDI beans required by the extension.
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
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerEntitiesForReflection(BuildProducer<ReflectiveClassBuildItem> reflectiveClasses,
                                       CombinedIndexBuildItem combinedIndex,
                                       MorphiumRecorder recorder) {
        List<String> entityClassNames = new ArrayList<>();
        IndexView index = combinedIndex.getIndex();

        DotName entityDotName = DotName.createSimple(Entity.class.getName());
        DotName embeddedDotName = DotName.createSimple(Embedded.class.getName());

        for (AnnotationInstance ai : index.getAnnotations(entityDotName)) {
            if (ai.target().kind() == org.jboss.jandex.AnnotationTarget.Kind.CLASS) {
                String className = ai.target().asClass().name().toString();
                registerClass(className, reflectiveClasses);
                entityClassNames.add(className);
            }
        }
        for (AnnotationInstance ai : index.getAnnotations(embeddedDotName)) {
            if (ai.target().kind() == org.jboss.jandex.AnnotationTarget.Kind.CLASS) {
                String className = ai.target().asClass().name().toString();
                registerClass(className, reflectiveClasses);
                // @Embedded classes are also pre-registered for typeId mapping
                entityClassNames.add(className);
            }
        }

        // Pass discovered @Entity/@Embedded classes to runtime so MorphiumProducer can
        // call ensureIndicesFor() and pre-register them with EntityRegistry —
        // this makes ClassGraph completely optional at runtime under Quarkus.
        // Always call setEntityClassNames (even when empty) to reset state on hot reload.
        if (!entityClassNames.isEmpty()) {
            log.infof("Morphium: passing %d @Entity/@Embedded classes for runtime pre-registration", entityClassNames.size());
        }
        recorder.setEntityClassNames(entityClassNames);
    }

    private void registerClass(String className,
                               BuildProducer<ReflectiveClassBuildItem> out) {
        log.debugf("Morphium: registering %s for reflection (native image)", className);
        out.produce(ReflectiveClassBuildItem.builder(className)
            .constructors(true)
            .methods(true)
            .fields(true)
            .build());
    }
}
