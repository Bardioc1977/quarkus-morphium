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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Unit tests for {@link MorphiumDevServicesProcessor} internals.
 *
 * <p>These tests do NOT start Docker containers. They verify:
 * <ul>
 *   <li>Static fields are {@code volatile} (live-reload visibility guarantee)</li>
 *   <li>{@code stopContainerIfRunning()} is null-safe (devContainer may be null)</li>
 *   <li>Mode-detection contract: {@code MongoDBContainer} IS-A {@code GenericContainer}
 *       but a plain {@code GenericContainer} is NOT-A {@code MongoDBContainer} – this
 *       is the {@code instanceof} check used for standalone vs. replica-set detection</li>
 *   <li>Shutdown hook guard: the {@code shutdownHookRegistered} flag is present and of
 *       the correct type</li>
 * </ul>
 */
@DisplayName("MorphiumDevServicesProcessor – static fields and null-safety")
class MorphiumDevServicesProcessorTest {

    // -------------------------------------------------------------------------
    // Volatile fields – live-reload visibility contract
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("devContainer field is volatile")
    void devContainer_isVolatile() throws Exception {
        Field f = MorphiumDevServicesProcessor.class.getDeclaredField("devContainer");
        assertThat(Modifier.isVolatile(f.getModifiers()))
                .as("devContainer must be volatile for cross-classloader visibility")
                .isTrue();
    }

    @Test
    @DisplayName("shutdownHookRegistered field is volatile")
    void shutdownHookRegistered_isVolatile() throws Exception {
        Field f = MorphiumDevServicesProcessor.class.getDeclaredField("shutdownHookRegistered");
        assertThat(Modifier.isVolatile(f.getModifiers()))
                .as("shutdownHookRegistered must be volatile")
                .isTrue();
    }

    @Test
    @DisplayName("shutdownHookRegistered field is boolean")
    void shutdownHookRegistered_isBoolean() throws Exception {
        Field f = MorphiumDevServicesProcessor.class.getDeclaredField("shutdownHookRegistered");
        assertThat(f.getType()).isEqualTo(boolean.class);
    }

    // -------------------------------------------------------------------------
    // Null-safety: stopContainerIfRunning must not throw when devContainer is null
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("stopContainerIfRunning() is null-safe when devContainer is null")
    void stopContainerIfRunning_doesNotThrowWhenNull() throws Exception {
        Field containerField = MorphiumDevServicesProcessor.class.getDeclaredField("devContainer");
        containerField.setAccessible(true);
        Object previous = containerField.get(null);

        containerField.set(null, null);
        try {
            Method stop = MorphiumDevServicesProcessor.class
                    .getDeclaredMethod("stopContainerIfRunning");
            stop.setAccessible(true);

            assertDoesNotThrow(() -> {
                try {
                    stop.invoke(null);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, "stopContainerIfRunning() must not throw when devContainer is null");
        } finally {
            containerField.set(null, previous);
        }
    }

    // -------------------------------------------------------------------------
    // Mode-detection type contract (documentation tests)
    //
    // MorphiumDevServicesProcessor.startDevServices() uses
    //     devContainer instanceof MongoDBContainer
    // to detect whether the running container is a replica set or standalone.
    // This relies on Testcontainers' class hierarchy: MongoDBContainer extends
    // GenericContainer but not vice versa. These tests document that contract
    // so that a Testcontainers upgrade that breaks it is caught immediately.
    //
    // The actual branching logic (standalone vs replica-set) is exercised by
    // MorphiumDevServicesConfigDefaultsTest (config binding) and the
    // integration test MorphiumDevServicesReplicaSetConfigTest (startup).
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("[contract] MongoDBContainer extends GenericContainer")
    void typeContract_mongoDbContainerExtendsGenericContainer() {
        assertThat(GenericContainer.class.isAssignableFrom(MongoDBContainer.class))
                .as("MongoDBContainer must extend GenericContainer")
                .isTrue();
    }

    @Test
    @DisplayName("[contract] GenericContainer is NOT a MongoDBContainer")
    void typeContract_genericContainerIsNotMongoDBContainer() {
        assertThat(MongoDBContainer.class.isAssignableFrom(GenericContainer.class))
                .as("A plain GenericContainer must NOT be assignable to MongoDBContainer")
                .isFalse();
    }

    // -------------------------------------------------------------------------
    // Shutdown hook guard: field is writable via reflection (for test reset)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("shutdownHookRegistered can be read and written via reflection")
    void shutdownHookRegistered_isAccessibleViaReflection() throws Exception {
        Field f = MorphiumDevServicesProcessor.class.getDeclaredField("shutdownHookRegistered");
        f.setAccessible(true);
        boolean original = (boolean) f.get(null);
        try {
            f.set(null, true);
            assertThat((boolean) f.get(null)).isTrue();
            f.set(null, false);
            assertThat((boolean) f.get(null)).isFalse();
        } finally {
            f.set(null, original);
        }
    }
}
