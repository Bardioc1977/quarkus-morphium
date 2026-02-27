package de.caluga.morphium.quarkus.it;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.quarkus.transaction.MorphiumTransactional;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Test service exercising {@link MorphiumTransactional} for integration tests.
 */
@ApplicationScoped
public class TransactionalService {

    @Inject
    Morphium morphium;

    @MorphiumTransactional
    public void storeSuccessfully(ItemEntity item) {
        morphium.store(item);
    }

    @MorphiumTransactional
    public void storeAndFail(ItemEntity item) {
        morphium.store(item);
        throw new RuntimeException("forced rollback");
    }
}
