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
package de.caluga.morphium.quarkus;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;
import java.util.Optional;

/**
 * Type-safe runtime configuration for the Morphium extension.
 *
 * <p>All properties are resolved from {@code application.properties} at
 * startup – no reflection, no Unsafe access, purely CDI/SmallRye Config.
 *
 * <p>Example {@code application.properties}:
 * <pre>{@code
 * quarkus.morphium.database=my-app-db
 * quarkus.morphium.hosts=mongo1:27017,mongo2:27017
 * quarkus.morphium.username=admin
 * quarkus.morphium.password=secret
 * quarkus.morphium.max-connections=250
 * }</pre>
 */
@ConfigMapping(prefix = "quarkus.morphium")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface MorphiumRuntimeConfig {

    /**
     * MongoDB host list in {@code host:port} format.
     * Multiple hosts are separated by commas in application.properties.
     */
    @WithDefault("localhost:27017")
    List<String> hosts();

    /** MongoDB database name. */
    String database();

    /** MongoDB username (optional). */
    Optional<String> username();

    /** MongoDB password (optional). */
    Optional<String> password();

    /** Authentication database, defaults to {@code admin}. */
    @WithDefault("admin")
    String authDatabase();

    /**
     * Read preference for MongoDB queries.
     * Accepted values: {@code primary}, {@code primaryPreferred},
     * {@code secondary}, {@code secondaryPreferred}, {@code nearest}.
     */
    @WithDefault("primary")
    String readPreference();

    /** Whether Morphium should automatically create / verify indexes on startup. */
    @WithDefault("true")
    boolean createIndexes();

    /** Maximum number of MongoDB connections in the pool. */
    @WithDefault("250")
    int maxConnections();

    /**
     * Optional MongoDB Atlas connection string ({@code mongodb+srv://...}).
     * When present this overrides {@link #hosts()}.
     */
    Optional<String> atlasUrl();

    /**
     * Morphium driver name. Defaults to {@code PooledDriver}.
     * Use {@code InMemDriver} for tests (no MongoDB required).
     */
    @WithDefault("PooledDriver")
    String driverName();

    /** Nested cache configuration. */
    CacheConfig cache();

    /** Nested TLS / X.509 configuration. */
    SslConfig ssl();

    /** Nested LocalDateTime serialization configuration. */
    LocalDateTimeConfig localDateTime();
}
