package de.caluga.morphium.quarkus.testing;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Quarkus test profile that configures Morphium to use the in-memory driver.
 *
 * <p>Apply this profile to any {@code @QuarkusTest} class that should run without
 * a MongoDB container:
 *
 * <pre>{@code
 * @QuarkusTest
 * @TestProfile(InMemMorphiumTestProfile.class)
 * class MyRepositoryTest { ... }
 * }</pre>
 *
 * <p>This profile sets the following configuration overrides:
 * <ul>
 *   <li>{@code morphium.driver-name=InMemDriver} – activates the in-process driver</li>
 *   <li>{@code morphium.database=inmem-test} – isolated test database name</li>
 *   <li>{@code quarkus.morphium.devservices.enabled=false} – prevents a MongoDB
 *       container from being started alongside the in-memory driver</li>
 * </ul>
 *
 * <p>Tests annotated with this profile can coexist with regular {@code @QuarkusTest}
 * classes that rely on Dev Services (a real MongoDB container). Quarkus restarts the
 * application context once for each distinct profile encountered in the test suite.
 */
public class InMemMorphiumTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "morphium.driver-name", "InMemDriver",
                "morphium.database", "inmem-test",
                "quarkus.morphium.devservices.enabled", "false"
        );
    }
}
