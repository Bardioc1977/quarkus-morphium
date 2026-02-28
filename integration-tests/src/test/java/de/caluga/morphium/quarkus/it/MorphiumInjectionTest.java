package de.caluga.morphium.quarkus.it;

import de.caluga.morphium.Morphium;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the extension correctly produces a {@link Morphium} CDI bean
 * and that it is operational (connected to the InMemDriver).
 */
@QuarkusTest
@DisplayName("Morphium CDI injection")
class MorphiumInjectionTest {

    @Inject
    Morphium morphium;

    @Test
    @DisplayName("Morphium bean is not null")
    void morphiumBeanIsProduced() {
        assertThat(morphium).isNotNull();
    }

    @Test
    @DisplayName("Morphium is connected (InMemDriver reports isConnected=true)")
    void morphiumIsConnected() {
        assertThat(morphium.getDriver().isConnected()).isTrue();
    }

    @Test
    @DisplayName("Morphium uses the configured database name")
    void morphiumUsesConfiguredDatabase() {
        assertThat(morphium.getConfig().connectionSettings().getDatabase()).isEqualTo("it-db");
    }
}
