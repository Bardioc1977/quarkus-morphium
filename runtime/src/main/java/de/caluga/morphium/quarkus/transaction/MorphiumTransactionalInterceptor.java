package de.caluga.morphium.quarkus.transaction;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.quarkus.transaction.MorphiumTransactionEvent.Phase;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

/**
 * CDI interceptor that wraps methods annotated with {@link MorphiumTransactional}
 * in a Morphium transaction.
 *
 * <ul>
 *   <li>Fires {@link Phase#BEFORE_COMMIT} before committing.</li>
 *   <li>Fires {@link Phase#AFTER_COMMIT} after a successful commit.</li>
 *   <li>On exception: aborts, fires {@link Phase#AFTER_ROLLBACK}, re-throws.</li>
 * </ul>
 */
@MorphiumTransactional
@Interceptor
@jakarta.annotation.Priority(Interceptor.Priority.PLATFORM_BEFORE + 200)
public class MorphiumTransactionalInterceptor {

    @Inject
    Morphium morphium;

    @Inject
    @MorphiumTxPhase(Phase.BEFORE_COMMIT)
    Event<MorphiumTransactionEvent> beforeCommit;

    @Inject
    @MorphiumTxPhase(Phase.AFTER_COMMIT)
    Event<MorphiumTransactionEvent> afterCommit;

    @Inject
    @MorphiumTxPhase(Phase.AFTER_ROLLBACK)
    Event<MorphiumTransactionEvent> afterRollback;

    @AroundInvoke
    Object aroundInvoke(InvocationContext ctx) throws Exception {
        morphium.startTransaction();
        try {
            Object result = ctx.proceed();
            beforeCommit.fire(new MorphiumTransactionEvent(Phase.BEFORE_COMMIT));
            morphium.commitTransaction();
            afterCommit.fire(new MorphiumTransactionEvent(Phase.AFTER_COMMIT));
            return result;
        } catch (Exception e) {
            morphium.abortTransaction();
            afterRollback.fire(new MorphiumTransactionEvent(Phase.AFTER_ROLLBACK, e));
            throw e;
        }
    }
}
