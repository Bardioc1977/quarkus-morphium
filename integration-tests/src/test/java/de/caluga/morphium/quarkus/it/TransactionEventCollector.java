package de.caluga.morphium.quarkus.it;

import de.caluga.morphium.quarkus.transaction.MorphiumTransactionEvent;
import de.caluga.morphium.quarkus.transaction.MorphiumTxPhase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static de.caluga.morphium.quarkus.transaction.MorphiumTransactionEvent.Phase.*;

/**
 * Collects {@link MorphiumTransactionEvent}s for test assertions.
 */
@ApplicationScoped
public class TransactionEventCollector {

    private final List<MorphiumTransactionEvent> events = new CopyOnWriteArrayList<>();

    void onBeforeCommit(@Observes @MorphiumTxPhase(BEFORE_COMMIT) MorphiumTransactionEvent e) {
        events.add(e);
    }

    void onAfterCommit(@Observes @MorphiumTxPhase(AFTER_COMMIT) MorphiumTransactionEvent e) {
        events.add(e);
    }

    void onAfterRollback(@Observes @MorphiumTxPhase(AFTER_ROLLBACK) MorphiumTransactionEvent e) {
        events.add(e);
    }

    public List<MorphiumTransactionEvent> getEvents() {
        return events;
    }

    public void clear() {
        events.clear();
    }
}
