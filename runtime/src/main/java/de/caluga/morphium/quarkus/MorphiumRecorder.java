package de.caluga.morphium.quarkus;

import io.quarkus.runtime.annotations.Recorder;

/**
 * Quarkus {@link Recorder} for the Morphium extension.
 *
 * <p>Currently a placeholder – the {@link MorphiumProducer} handles all
 * lifecycle work at CDI startup/shutdown, so no build-time bytecode
 * recording is necessary.  The class is kept as an extension point for
 * future features (e.g. pre-warming the entity cache at native image build
 * time).
 */
@Recorder
public class MorphiumRecorder {
    // reserved for future build-time initialisation steps
}
