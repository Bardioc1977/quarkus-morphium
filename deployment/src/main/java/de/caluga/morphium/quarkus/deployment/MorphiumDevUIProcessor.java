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
package de.caluga.morphium.quarkus.deployment;

import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

import org.eclipse.microprofile.config.ConfigProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers the Morphium extension in the Quarkus Dev UI.
 *
 * <p>Produces a {@link CardPageBuildItem} that displays the MongoDB Dev Services
 * connection info (host, port, database) in the Dev UI at {@code /q/dev-ui/}.
 */
public class MorphiumDevUIProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    void createCard(List<DevServicesResultBuildItem> devServicesResults,
                    MorphiumDevServicesBuildTimeConfig devServicesConfig,
                    BuildProducer<CardPageBuildItem> cardProducer) {

        // Find our dev services result by feature name
        Map<String, String> devConfig = null;
        String containerId = null;
        for (DevServicesResultBuildItem result : devServicesResults) {
            if (MorphiumFeature.FEATURE_NAME.equals(result.getName())) {
                devConfig = result.getConfig();
                containerId = result.getContainerId();
                break;
            }
        }

        CardPageBuildItem card = new CardPageBuildItem();

        // --- Library version labels (shown at card footer, like Kafka/ArC) ---
        card.addLibraryVersion("de.caluga", "morphium",
                "Morphium", "https://github.com/sboesebeck/morphium");
        card.addLibraryVersion("io.quarkiverse.morphium", "quarkus-morphium",
                "Quarkus Morphium Extension", "https://github.com/Bardioc1977/quarkus-morphium");
        card.addLibraryVersion("jakarta.data", "jakarta.data-api",
                "Jakarta Data", "https://jakarta.ee/specifications/data/");

        // --- MongoDB Connection page ---
        List<Map<String, String>> rows = new ArrayList<>();
        if (devConfig != null) {
            String shortId = containerId != null
                    ? containerId.substring(0, Math.min(12, containerId.length()))
                    : "n/a";
            String mode = devServicesConfig.replicaSet()
                    ? "Replica Set (transactions enabled)"
                    : "Standalone";
            rows.add(row("Hosts",        devConfig.getOrDefault("quarkus.morphium.hosts", "n/a")));
            rows.add(row("Database",     devConfig.getOrDefault("quarkus.morphium.database", "n/a")));
            rows.add(row("Mode",         mode));
            rows.add(row("Container ID", shortId));
            rows.add(row("Status",       "Running"));
        } else {
            // No Dev Services container — read connection info from application config
            var config = ConfigProvider.getConfig();
            String hosts = config.getOptionalValue("quarkus.morphium.hosts", String.class)
                    .orElse("n/a");
            String database = config.getOptionalValue("quarkus.morphium.database", String.class)
                    .orElse("n/a");
            boolean hasReplicaSet = config.getOptionalValue("quarkus.morphium.replica-set-name", String.class)
                    .isPresent() || devServicesConfig.replicaSet();
            String mode = hasReplicaSet
                    ? "Replica Set (transactions enabled)"
                    : "Standalone";
            rows.add(row("Hosts",        hosts));
            rows.add(row("Database",     database));
            rows.add(row("Mode",         mode));
            rows.add(row("Container ID", "—"));
            rows.add(row("Status",       "External MongoDB"));
        }
        card.addBuildTimeData("connectionInfo", rows);

        card.addPage(Page.tableDataPageBuilder("MongoDB Connection")
                .icon("font-awesome-solid:database")
                .buildTimeDataKey("connectionInfo"));

        cardProducer.produce(card);
    }

    /** Creates a row map with guaranteed key order (Property first, Value second). */
    private static Map<String, String> row(String property, String value) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("Property", property);
        map.put("Value", value);
        return map;
    }
}
