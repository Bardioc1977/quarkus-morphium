package de.caluga.morphium.quarkus.it;

import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the Dev Services build-time config defaults are correct
 * and that the InMemDriver test profile suppresses Dev Services as expected.
 *
 * <p>In this test profile {@code quarkus.morphium.devservices.enabled=false}
 * and {@code morphium.driver-name=InMemDriver} are set via application.properties,
 * so no container is started.
 */
@QuarkusTest
@DisplayName("Dev Services configuration")
class MorphiumDevServicesConfigTest {

    @Test
    @DisplayName("Dev Services disabled in test profile – no container host override")
    void devServicesDisabled_hostsNotOverridden() {
        // With devservices.enabled=false and InMemDriver, morphium.hosts is never
        // injected by the DevServicesProcessor. The config value stays absent
        // (or at the @WithDefault "localhost:27017").
        var hosts = ConfigProvider.getConfig()
                .getOptionalValue("morphium.hosts", String.class);

        // Either absent (user never set it) or the default – never a random container port.
        // Container ports are typically >= 30000; the MongoDB default port is 27017.
        hosts.ifPresent(h ->
            assertThat(h)
                .as("hosts must not be a dev-services container port in test profile")
                .satisfiesAnyOf(
                    v -> assertThat(v).isEqualTo("localhost:27017"),
                    v -> assertThat(Integer.parseInt(v.split(":")[1])).isLessThan(30000)
                )
        );
    }

    @Test
    @DisplayName("morphium.database config property is readable")
    void databaseConfigIsReadable() {
        String db = ConfigProvider.getConfig()
                .getValue("morphium.database", String.class);
        assertThat(db).isEqualTo("it-db");
    }

    @Test
    @DisplayName("morphium.driver-name config property is readable")
    void driverNameConfigIsReadable() {
        String driver = ConfigProvider.getConfig()
                .getValue("morphium.driver-name", String.class);
        assertThat(driver).isEqualTo("InMemDriver");
    }
}
