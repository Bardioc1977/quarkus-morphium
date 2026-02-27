package de.caluga.morphium.quarkus.transaction;

import jakarta.inject.Qualifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * CDI qualifier used to observe {@link MorphiumTransactionEvent}s for a
 * specific {@link MorphiumTransactionEvent.Phase}.
 *
 * <pre>{@code
 * void onCommit(@Observes @MorphiumTxPhase(AFTER_COMMIT) MorphiumTransactionEvent e) { ... }
 * }</pre>
 */
@Qualifier
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface MorphiumTxPhase {
    MorphiumTransactionEvent.Phase value();
}
