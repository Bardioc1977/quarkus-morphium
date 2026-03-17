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

import io.quarkus.deployment.builditem.Startable;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.ImageNameSubstitutor;

/**
 * {@link Startable} wrapper around a Testcontainers MongoDB container.
 * Used by Quarkus's {@code owned()} Dev Services API so that the framework
 * manages the container lifecycle and reuses it across augmentation phases
 * via the cross-classloader {@code RunningDevServicesRegistry}.
 */
class MongoDBStartable implements Startable {

    private static final int MONGO_PORT = 27017;

    private final String imageName;
    private final boolean replicaSet;
    private GenericContainer<?> container;

    MongoDBStartable(String imageName, boolean replicaSet) {
        this.imageName = imageName;
        this.replicaSet = replicaSet;
    }

    @Override
    @SuppressWarnings("resource")
    public void start() {
        if (container != null) {
            return; // Already started
        }
        // Apply the image name substitutor ourselves (e.g. hub.image.name.prefix from
        // testcontainers.properties) and mark the result as a compatible substitute.
        // This prevents Testcontainers from applying the substitutor a second time,
        // and ensures the image name matches what Docker has cached locally.
        DockerImageName base = DockerImageName.parse(imageName);
        DockerImageName substituted = ImageNameSubstitutor.instance().apply(base)
                .asCompatibleSubstituteFor("mongo");

        if (replicaSet) {
            container = new MongoDBContainer(substituted).withReplicaSet();
        } else {
            container = new GenericContainer<>(substituted)
                    .withExposedPorts(MONGO_PORT)
                    .waitingFor(Wait.forLogMessage(".*Waiting for connections.*\n", 1));
        }
        container.start();
    }

    @Override
    public void close() {
        if (container != null && container.isRunning()) {
            container.stop();
        }
    }

    @Override
    public String getConnectionInfo() {
        ensureStarted();
        return getHost() + ":" + getMappedPort();
    }

    String getHost() {
        ensureStarted();
        return container.getHost();
    }

    @Override
    public String getContainerId() {
        return container != null ? container.getContainerId() : null;
    }

    int getMappedPort() {
        ensureStarted();
        return container.getMappedPort(MONGO_PORT);
    }

    private void ensureStarted() {
        if (container == null) {
            throw new IllegalStateException("MongoDBStartable has not been started yet");
        }
    }

    boolean isReplicaSet() {
        return replicaSet;
    }

    String getReplicaSetName() {
        if (container instanceof MongoDBContainer mongoContainer) {
            String connStr = mongoContainer.getConnectionString();
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("[?&]replicaSet=([^&]+)")
                    .matcher(connStr);
            return m.find() ? m.group(1) : "docker-rs";
        }
        return null;
    }
}
