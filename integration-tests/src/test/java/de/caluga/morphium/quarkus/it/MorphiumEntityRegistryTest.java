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

import de.caluga.morphium.AnnotationAndReflectionHelper;
import de.caluga.morphium.EntityRegistry;
import de.caluga.morphium.Morphium;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the Quarkus build-time entity discovery (Jandex scan in
 * {@code MorphiumProcessor}) correctly pre-registers {@code @Entity} and
 * {@code @Embedded} classes via {@link de.caluga.morphium.quarkus.MorphiumRecorder}
 * and {@link EntityRegistry}.
 *
 * <p>This is an explicit test for the ClassGraph-optional flow:
 * the Processor discovers entities at build time, the Recorder stores
 * class names, and the Producer pre-registers them with EntityRegistry.
 */
@QuarkusTest
@DisplayName("Build-time entity pre-registration (EntityRegistry)")
class MorphiumEntityRegistryTest {

    @Inject
    Morphium morphium;

    @Test
    @DisplayName("EntityRegistry has pre-registered entities after Morphium init")
    void entityRegistry_hasPreRegisteredEntities() {
        assertThat(EntityRegistry.hasPreRegisteredEntities()).isTrue();
    }

    @Test
    @DisplayName("Pre-registered entities include @Entity classes from Jandex scan")
    void preRegisteredEntities_containEntityClasses() {
        assertThat(EntityRegistry.getPreRegisteredEntities())
                .as("Pre-registered set should contain @Entity classes discovered by Jandex")
                .contains(CustomerEntity.class, OrderEntity.class, ItemEntity.class);
    }

    @Test
    @DisplayName("Pre-registered entities include @Embedded classes from Jandex scan")
    void preRegisteredEntities_containEmbeddedClasses() {
        assertThat(EntityRegistry.getPreRegisteredEntities())
                .as("Pre-registered set should contain @Embedded classes discovered by Jandex")
                .contains(AddressEmbedded.class);
    }

    @Test
    @DisplayName("Pre-registered typeIds map contains FQCN entries")
    void preRegisteredTypeIds_containFqcnEntries() {
        assertThat(EntityRegistry.getPreRegisteredTypeIds())
                .as("TypeId map should contain FQCN→FQCN mappings for discovered entities")
                .containsKey(CustomerEntity.class.getName())
                .containsKey(AddressEmbedded.class.getName());
    }

    @Test
    @DisplayName("ObjectMapper resolves collection name for pre-registered @Entity")
    void objectMapper_resolvesCollectionName() {
        String collName = morphium.getMapper().getCollectionName(CustomerEntity.class);
        assertThat(collName).isEqualTo("it_customers");
    }

    @Test
    @DisplayName("ObjectMapper resolves class for pre-registered collection name")
    void objectMapper_resolvesClassForCollectionName() {
        Class<?> resolved = morphium.getMapper().getClassForCollectionName("it_customers");
        assertThat(resolved).isEqualTo(CustomerEntity.class);
    }

    @Test
    @DisplayName("TypeId resolution works for pre-registered @Entity")
    void typeIdResolution_worksForEntity() throws Exception {
        AnnotationAndReflectionHelper arh = new AnnotationAndReflectionHelper(true);
        Class<?> resolved = arh.getClassForTypeId(CustomerEntity.class.getName());
        assertThat(resolved).isEqualTo(CustomerEntity.class);
    }
}
