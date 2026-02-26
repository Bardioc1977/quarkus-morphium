package de.caluga.morphium.quarkus;

import io.smallrye.config.WithDefault;

/**
 * Cache configuration group, nested under {@link MorphiumRuntimeConfig#cache()}.
 */
public interface CacheConfig {

    /** Global validity time for cached query results in milliseconds. */
    @WithDefault("60000")
    long globalValidTime();

    /** Whether query-result caching is enabled. */
    @WithDefault("true")
    boolean readCacheEnabled();
}
