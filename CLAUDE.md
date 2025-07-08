# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ASquare is a comprehensive Java library for semantic web development, focusing on RDF data management, SPARQL querying, and semantic application development. It's a multi-module Gradle project with 25 modules providing various functionalities for working with RDF triplestores, application profiles, and semantic data.

## Build and Development Commands

### Building the Project
```bash
# Clean and build all modules
./gradlew clean build

# Build without running tests
./gradlew build -x test

# Build a specific module
./gradlew :access:build
./gradlew :application-profile:build

# Install to local Maven repository
./gradlew publishToMavenLocal
```

### Running Tests
```bash
# Run all tests
./gradlew test

# Run tests for a specific module
./gradlew :access:test
./gradlew :cogni-core:test

# Run a single test class
./gradlew :access:test --tests "zone.cogni.asquare.access.simplerdf.SimpleRdfAccessServicePropertiesTest"

# Run tests with debug output
./gradlew test --debug
```

### Code Quality and Security
```bash
# Run OWASP dependency vulnerability check
./gradlew dependencyCheckAnalyze

# Generate project dependency reports
./gradlew projectReport
```

### Publishing
```bash
# Publish to Cognizone Archiva
./gradlew publish -DpublishToCognizoneArchiva -Darchiva.username=xxx -Darchiva.password=xxx

# Publish to Cognizone Nexus
./gradlew publish -DpublishToCognizoneNexus -Dnexus.username=xxx -Dnexus.password=xxx

# Publish to Maven Central staging
./gradlew publish -DpublishToMavenCentral -Dossrh.username=xxx -Dossrh.password=xxx
```

## Architecture and Module Structure

### Core Architecture
The project follows a modular architecture with clear separation of concerns:

1. **Core Foundation** (`cogni-core`, `cogni-libs`, `cogni-sem`): Basic utilities, Spring integration, and semantic web utilities
2. **RDF Access Layer** (`access`): Provides interfaces for accessing RDF data through SPARQL, with implementations for various triplestores
3. **Application Profiles** (`application-profile*`): JSON-based configuration for defining RDF data models and validation rules
4. **Storage Backends** (`triplestore-*`): Different implementations for RDF storage (in-memory, TDB2, pooled connections)
5. **Elasticsearch Integration** (`elastic-*`): Indexing and searching RDF data
6. **Security** (`security`, `security2`): SAML authentication and Spring Security integration

### Key Design Patterns

1. **Service Pattern**: Most functionality is exposed through Spring services (e.g., `AccessService`, `SparqlService`)
2. **Factory Pattern**: Used for creating views and configurations (e.g., `ApplicationViewFactory`)
3. **Aspect-Oriented Programming**: Used for logging (`@LoggedAction`) and async operations (`@Async`)
4. **Builder Pattern**: Used for constructing complex objects (e.g., `TypedResourceBuilder`)

### Module Dependencies
- All modules depend on `cogni-core` for basic utilities
- `access` module is central to RDF operations
- `application-profile` modules build on top of `access` for model validation
- `asquareroot` provides test utilities for all modules

## Key Technologies and Versions

- **Java**: 11
- **Spring Boot**: 2.7.18
- **Spring Security**: 5.8.9
- **Apache Jena**: 4.10.0 (RDF/SPARQL framework)
- **Elasticsearch**: 7.5.2
- **Lombok**: For reducing boilerplate code
- **JUnit**: 5 (via Spring Boot Test)

## Testing Approach

### Test Utilities (asquareroot module)
- `@EnableEmbeddedElastic`: Provides embedded Elasticsearch for integration tests
- `InMemoryRdfStoreService`: In-memory RDF store for unit tests
- `@WithMockAsquareUser`: Mock authentication for security tests
- Base test classes: `A2EmbeddedEnvironmentsJUnit5` for common test setup

### Test Data
- Application profiles: `.ap.json` files define data models
- RDF test data: `.ttl`, `.rdf`, `.trig` files contain sample RDF data
- SPARQL queries: `.sparql` files for testing query functionality

## Development Notes

1. **Lombok Configuration**: The project uses Lombok with chained accessors enabled. Ensure your IDE has Lombok support.

2. **Memory Settings**: Some modules (like `triplestore-jena-memory`) have specific JVM memory settings for tests.

3. **Feature Flags**: The project uses feature flags (see `FeatureFlag.java`) for controlling functionality.

4. **Spring Profiles**: Different Spring configurations are available for various environments.

5. **RDF Focus**: This is primarily an RDF/semantic web library. Understanding RDF, SPARQL, and semantic web concepts is essential.

6. **Application Profiles**: The `.ap.json` files define JSON-based schemas for RDF data validation and are central to the framework's functionality.

## Common Development Tasks

When implementing new features:
1. Add appropriate Spring service annotations
2. Include `@LoggedAction` for operations that should be logged
3. Write tests using the utilities in `asquareroot`
4. Follow the existing module structure and naming conventions
5. Update application profiles if adding new RDF types or properties

## Java 17 Compatibility

The async-indexing module has been updated to be Java 17 compatible by removing reflection on JDK internals:
- `AsyncUtils` now uses ThreadLocal for context storage instead of reflection
- `AsyncAspect` captures context before task submission
- `AsyncRunnable` preserves context during execution
- Tests have been updated to work without reflection-based task unwrapping