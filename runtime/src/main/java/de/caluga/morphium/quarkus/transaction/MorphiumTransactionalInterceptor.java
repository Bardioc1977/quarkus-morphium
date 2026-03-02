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
package de.caluga.morphium.quarkus.transaction;

import org.jboss.logging.Logger;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.quarkus.transaction.MorphiumTransactionEvent.Phase;
import jakarta.annotation.PostConstruct;
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
 *   <li>On CosmosDB: skips transaction wrapping entirely; the method executes
 *       directly. A one-time WARN is logged at startup and per-call at DEBUG.</li>
 * </ul>
 */
@MorphiumTransactional
@Interceptor
@jakarta.annotation.Priority(Interceptor.Priority.PLATFORM_BEFORE + 200)
public class MorphiumTransactionalInterceptor {

    private static final Logger log = Logger.getLogger(MorphiumTransactionalInterceptor.class);

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

    private boolean cosmosDb;

    @PostConstruct
    void init() {
        try {
            cosmosDb = morphium.getDriver().isCosmosDB();
        } catch (Exception e) {
            log.warnf("Could not determine if backend is CosmosDB; assuming standard MongoDB. Cause: %s", e.getMessage());
            cosmosDb = false;
        }
        if (cosmosDb) {
            log.warn("CosmosDB detected — @MorphiumTransactional methods will execute "
                    + "WITHOUT transaction wrapping. Individual ops remain atomic; "
                    + "multi-document rollback is unavailable.");
        }
    }

    @AroundInvoke
    Object aroundInvoke(InvocationContext ctx) throws Exception {
        // CosmosDB: execute without transaction wrapping
        if (cosmosDb) {
            log.debugf("CosmosDB: @MorphiumTransactional on %s.%s executes WITHOUT transaction.",
                    ctx.getMethod().getDeclaringClass().getSimpleName(),
                    ctx.getMethod().getName());
            return ctx.proceed();
        }

        try {
            morphium.startTransaction();
        } catch (UnsupportedOperationException e) {
            // Defensive fallback: init() missed CosmosDB detection (e.g. driver not yet connected)
            cosmosDb = true;
            log.warn("startTransaction() threw UnsupportedOperationException — "
                    + "switching to CosmosDB mode for all future invocations.");
            return ctx.proceed();
        }
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
