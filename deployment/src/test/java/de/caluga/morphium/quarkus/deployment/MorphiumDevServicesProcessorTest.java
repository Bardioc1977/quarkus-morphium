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
import org.testcontainers.mongodb.MongoDBContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MorphiumDevServicesProcessor} and {@link MongoDBStartable}.
 *
 * <p>These tests do NOT start Docker containers. They verify:
 * <ul>
 *   <li>Mode-detection contract: {@code MongoDBContainer} IS-A {@code GenericContainer}
 *       but a plain {@code GenericContainer} is NOT-A {@code MongoDBContainer}</li>
 *   <li>{@code MongoDBStartable} construction and property access</li>
 * </ul>
 */
@DisplayName("MorphiumDevServicesProcessor – owned() API and container types")
class MorphiumDevServicesProcessorTest {

    // -------------------------------------------------------------------------
    // Mode-detection type contract (documentation tests)
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
    // MongoDBStartable construction
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("MongoDBStartable stores replicaSet flag")
    void startable_storesReplicaSetFlag() {
        var standalone = new MongoDBStartable("mongo:8", false);
        assertThat(standalone.isReplicaSet()).isFalse();

        var replicaSet = new MongoDBStartable("mongo:8", true);
        assertThat(replicaSet.isReplicaSet()).isTrue();
    }

    @Test
    @DisplayName("MongoDBStartable.getContainerId() returns null before start")
    void startable_containerIdNullBeforeStart() {
        var startable = new MongoDBStartable("mongo:8", false);
        assertThat(startable.getContainerId()).isNull();
    }
}
