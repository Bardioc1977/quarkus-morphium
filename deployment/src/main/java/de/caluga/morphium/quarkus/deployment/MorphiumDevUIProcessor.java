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

        if (devConfig != null) {
            String hosts = devConfig.getOrDefault("quarkus.morphium.hosts", "n/a");
            String database = devConfig.getOrDefault("quarkus.morphium.database", "n/a");
            card.addBuildTimeData("hosts", hosts);
            card.addBuildTimeData("database", database);
            card.addBuildTimeData("containerId",
                    containerId != null ? containerId.substring(0, Math.min(12, containerId.length())) : "n/a");
            card.addBuildTimeData("status", "Running");
        } else {
            card.addBuildTimeData("hosts", "n/a");
            card.addBuildTimeData("database", "n/a");
            card.addBuildTimeData("containerId", "n/a");
            card.addBuildTimeData("status", "Not started (Dev Services disabled or quarkus.morphium.hosts set)");
        }

        card.addPage(Page.tableDataPageBuilder("MongoDB Connection")
                .icon("font-awesome-solid:database")
                .buildTimeDataKey("hosts"));

        cardProducer.produce(card);
    }
}
