package de.caluga.morphium.quarkus.deployment;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Marker build item that indicates the Morphium extension is active.
 * Used by {@link MorphiumProcessor} to signal feature registration.
 */
public final class MorphiumFeature extends SimpleBuildItem {
    static final String FEATURE_NAME = "morphium";
}
