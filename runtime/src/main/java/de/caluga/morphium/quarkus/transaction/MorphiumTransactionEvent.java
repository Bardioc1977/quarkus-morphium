package de.caluga.morphium.quarkus.transaction;

/**
 * CDI event fired by {@link MorphiumTransactionalInterceptor} at various
 * transaction lifecycle phases.
 */
public class MorphiumTransactionEvent {

    public enum Phase {
        BEFORE_COMMIT,
        AFTER_COMMIT,
        AFTER_ROLLBACK
    }

    private final Phase phase;
    private final Exception failure;

    public MorphiumTransactionEvent(Phase phase) {
        this(phase, null);
    }

    public MorphiumTransactionEvent(Phase phase, Exception failure) {
        this.phase = phase;
        this.failure = failure;
    }

    public Phase getPhase() {
        return phase;
    }

    /** Non-null only for {@link Phase#AFTER_ROLLBACK}. */
    public Exception getFailure() {
        return failure;
    }
}
