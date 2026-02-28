# Contributing to Quarkus Morphium Extension

Thank you for your interest in contributing! This document provides guidelines and
information for contributors.

## How to Contribute

### Reporting Bugs

- Use [GitHub Issues](https://github.com/Bardioc1977/quarkus-morphium/issues) to report bugs
- Include the Quarkus version, Morphium version, and Java version
- Provide a minimal reproducer if possible
- Describe the expected and actual behavior

### Suggesting Features

- Open a [GitHub Issue](https://github.com/Bardioc1977/quarkus-morphium/issues) describing the feature
- Explain the motivation and use case
- Discuss the proposed approach before starting implementation

### Submitting Pull Requests

1. Fork the repository and create a feature branch from `main`
2. Make your changes following the code conventions below
3. Add or update tests as appropriate
4. Update documentation if your change affects user-facing behavior
5. Ensure `mvn verify` passes locally
6. Open a pull request against `main`

## Development Setup

### Prerequisites

- JDK 21+
- Maven 3.9+
- Docker (for Dev Services integration tests)

### Building from Source

```bash
# Clone the Morphium dependency (SNAPSHOT required)
git clone https://github.com/Bardioc1977/morphium.git
cd morphium
mvn install -DskipTests
cd ..

# Clone and build the extension
git clone https://github.com/Bardioc1977/quarkus-morphium.git
cd quarkus-morphium
mvn verify
```

### Running Tests

```bash
# All tests (integration tests use InMemDriver, no Docker needed)
mvn test

# Full verification including compilation checks
mvn verify
```

## Code Conventions

- Java 21+ features are welcome (records, sealed classes, pattern matching, etc.)
- Follow existing code style (4-space indentation, no tabs)
- All Java source files **must** include the Apache 2.0 copyright header:

```java
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
```

- No `sun.*` or `jdk.internal.*` imports
- No `Unsafe` access or `--add-opens` hacks

## License

By contributing to this project, you agree that your contributions will be licensed
under the [Apache License 2.0](LICENSE).
