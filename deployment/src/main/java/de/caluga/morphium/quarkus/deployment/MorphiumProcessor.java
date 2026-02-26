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
import de.caluga.morphium.quarkus.MorphiumProducer;
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
            .addBeanClasses(MorphiumProducer.class)
            .setUnremovable()
            .build();
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
