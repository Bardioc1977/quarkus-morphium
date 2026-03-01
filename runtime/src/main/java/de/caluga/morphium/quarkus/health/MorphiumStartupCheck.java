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
package de.caluga.morphium.quarkus.health;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.driver.MorphiumDriver;
import de.caluga.morphium.driver.MorphiumDriver.DriverStatsKey;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Startup;

import java.util.Map;

/**
 * Startup health check for Morphium.
 *
 * <p>Reports DOWN until the initial connection has been established.
 * A DOWN startup probe causes Kubernetes to defer liveness and readiness probes.
 */
@Startup
@ApplicationScoped
public class MorphiumStartupCheck implements HealthCheck {

    @Inject
    Morphium morphium;

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.named("Morphium startup check");
        try {
            MorphiumDriver driver = morphium.getDriver();
            boolean connected = driver.isConnected();

            Map<DriverStatsKey, Double> stats = driver.getDriverStats();
            double opened = stats.getOrDefault(DriverStatsKey.CONNECTIONS_OPENED, 0.0);

            builder.withData("database", morphium.getConfig().connectionSettings().getDatabase())
                   .withData("connectionsOpened", (long) opened);

            return builder.status(connected).build();
        } catch (Exception e) {
            return builder.down().withData("error", e.getMessage()).build();
        }
    }
}
