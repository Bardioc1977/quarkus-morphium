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

import de.caluga.morphium.Morphium;
import de.caluga.morphium.quarkus.transaction.MorphiumTransactionEvent;
import de.caluga.morphium.quarkus.transaction.MorphiumTransactionEvent.Phase;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@code @MorphiumTransactional} interceptor and
 * transaction lifecycle events.
 */
@QuarkusTest
@DisplayName("@MorphiumTransactional interceptor + events")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MorphiumTransactionalTest {

    @Inject
    Morphium morphium;

    @Inject
    TransactionalService service;

    @Inject
    TransactionEventCollector eventCollector;

    @BeforeEach
    void clearEvents() {
        eventCollector.clear();
    }

    @Test
    @Order(1)
    @DisplayName("commit on success – entity is persisted")
    void commit_onSuccess() {
        var item = new ItemEntity();
        item.setName("tx-success");
        item.setPrice(42.0);

        service.storeSuccessfully(item);

        ItemEntity found = morphium.createQueryFor(ItemEntity.class)
                .f("name").eq("tx-success")
                .get();
        assertThat(found).isNotNull();
        assertThat(found.getPrice()).isEqualTo(42.0);
    }

    @Test
    @Order(2)
    @DisplayName("rollback on exception – entity is NOT persisted")
    void rollback_onException() {
        var item = new ItemEntity();
        item.setName("tx-fail");
        item.setPrice(99.0);

        assertThatThrownBy(() -> service.storeAndFail(item))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("forced rollback");

        ItemEntity found = morphium.createQueryFor(ItemEntity.class)
                .f("name").eq("tx-fail")
                .get();
        assertThat(found).isNull();
    }

    @Test
    @Order(3)
    @DisplayName("BEFORE_COMMIT + AFTER_COMMIT events fired on success")
    void events_firedOnCommit() {
        var item = new ItemEntity();
        item.setName("tx-events-commit");

        service.storeSuccessfully(item);

        assertThat(eventCollector.getEvents())
                .extracting(MorphiumTransactionEvent::getPhase)
                .containsExactly(Phase.BEFORE_COMMIT, Phase.AFTER_COMMIT);

        assertThat(eventCollector.getEvents())
                .allSatisfy(e -> assertThat(e.getFailure()).isNull());
    }

    @Test
    @Order(4)
    @DisplayName("AFTER_ROLLBACK event fired on exception, with failure")
    void events_firedOnRollback() {
        var item = new ItemEntity();
        item.setName("tx-events-rollback");

        assertThatThrownBy(() -> service.storeAndFail(item))
                .isInstanceOf(RuntimeException.class);

        assertThat(eventCollector.getEvents())
                .extracting(MorphiumTransactionEvent::getPhase)
                .containsExactly(Phase.AFTER_ROLLBACK);

        assertThat(eventCollector.getEvents().get(0).getFailure())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("forced rollback");
    }
}
