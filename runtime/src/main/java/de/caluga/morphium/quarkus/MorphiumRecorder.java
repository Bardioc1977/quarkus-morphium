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
