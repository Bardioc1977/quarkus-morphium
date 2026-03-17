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

import io.quarkus.runtime.annotations.Recorder;

import java.util.Collections;
import java.util.List;

/**
 * Quarkus {@link Recorder} for the Morphium extension.
 *
 * <p>Stores the list of {@code @Entity} class names discovered at build time
 * so that {@link MorphiumProducer} can call {@code ensureIndicesFor()} at
 * runtime. This is necessary because Morphium's built-in ClassGraph scan
 * does not work with Quarkus's classloader.
 */
@Recorder
public class MorphiumRecorder {

    private static volatile List<String> entityClassNames = Collections.emptyList();

    public void setEntityClassNames(List<String> classNames) {
        entityClassNames = classNames;
    }

    static List<String> getEntityClassNames() {
        return entityClassNames;
    }
}
