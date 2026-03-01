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
package de.caluga.morphium.quarkus.it;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying that {@code quarkus.morphium.devservices.replica-set=true}
 * does not prevent Quarkus application startup.
 *
 * <p>Dev Services are disabled in this profile ({@code devservices.enabled=false}) so
 * no MongoDB container is started. This test verifies the config key is accepted by
 * Quarkus without causing startup errors. The actual {@code @ConfigMapping} binding
 * (property-name → method) is covered by
 * {@code MorphiumDevServicesConfigDefaultsTest} in the deployment module.
 */
@QuarkusTest
@TestProfile(MorphiumDevServicesReplicaSetConfigTest.ReplicaSetEnabledProfile.class)
@DisplayName("Dev Services – replica-set config key (no container)")
class MorphiumDevServicesReplicaSetConfigTest {

    /**
     * Test profile that enables the {@code replica-set} flag while keeping
     * Dev Services disabled so no Docker container is started.
     */
    public static class ReplicaSetEnabledProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.morphium.driver-name",             "InMemDriver",
                    "quarkus.morphium.database",                "replset-cfg-test",
                    "quarkus.morphium.devservices.enabled",     "false",
                    "quarkus.morphium.devservices.replica-set", "true"
            );
        }
    }

    @Test
    @DisplayName("replica-set=true with devservices.enabled=false does not prevent startup")
    void replicaSet_withDevServicesDisabled_appStartsSuccessfully() {
        // Reaching this point means the Quarkus application started without errors
        // despite replica-set=true being set. This proves the config key is accepted
        // by the Quarkus config system and does not cause an unknown-property failure.
        //
        // NOTE: This does NOT prove that @ConfigMapping binds the key to
        // MorphiumDevServicesBuildTimeConfig.replicaSet(). That binding is tested
        // by MorphiumDevServicesConfigDefaultsTest in the deployment module using
        // SmallRyeConfigBuilder.withMapping().
        assertThat(ConfigProvider.getConfig()
                .getValue("quarkus.morphium.devservices.enabled", String.class))
                .isEqualTo("false");
        assertThat(ConfigProvider.getConfig()
                .getValue("quarkus.morphium.devservices.replica-set", String.class))
                .as("replica-set profile override must be present")
                .isEqualTo("true");
    }

    @Test
    @DisplayName("driver is InMemDriver (no MongoDB connection needed)")
    void driver_isInMemory() {
        assertThat(ConfigProvider.getConfig()
                .getValue("quarkus.morphium.driver-name", String.class))
                .isEqualTo("InMemDriver");
    }
}
