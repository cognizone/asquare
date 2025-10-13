# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build System and Common Commands

This is a multi-module Gradle project using Spring Boot 2.7.18 and Java 11.

### Build Commands
```bash
# Build all modules
./gradlew build

# Build specific module
./gradlew :cube:build

# Run tests for all modules
./gradlew test

# Run tests for specific module
./gradlew :cube:test

# Clean build
./gradlew clean build

# Publish to local Maven repository
./gradlew publishToMavenLocal
```

### Development Commands
```bash
# Generate dependency reports
./gradlew dependencyReport

# Check for security vulnerabilities
./gradlew dependencyCheckAnalyze

# Generate Javadoc
./gradlew javadoc
```

## Architecture Overview

**asquare** is a Java semantic development library that provides a comprehensive framework for working with RDF data, application profiles, and semantic web technologies. The architecture follows a layered approach with clear separation of concerns.

### Core Modules

**Foundation Layer:**
- `cogni-core`: Core utilities, JSON handling, Spring configurations
- `cogni-libs`: Jena integration, HTTP clients, basic semantic operations
- `cogni-sem`: Semantic web abstractions, RDF utilities, prefix management

**Data Access Layer:**
- `access`: Main access service abstraction, TypedResource pattern
- `triplestore*`: RDF store implementations (Jena Memory, TDB2, generic SPARQL endpoints)
- `elastic-service`: Elasticsearch abstraction layer

**Business Logic Layer:**
- `cube`: Core data transformation engine, JSON↔RDF conversion, SPARQL operations
- `service`: High-level business services, data processing pipelines

**Configuration Layer:**
- `application-profile*`: Schema definition system using JSON-based profiles
- Application profiles define types, attributes, validation rules, and drive all data operations

**Infrastructure Layer:**
- `security*`: Authentication, authorization, SAML integration
- `async-indexing`: Asynchronous data indexing
- `action-logger`: Request/response logging and auditing

### Key Architectural Patterns

1. **Application Profile-Driven Development**: All data operations are constrained by JSON-based application profiles that define semantic schemas
2. **TypedResource Pattern**: Strongly-typed resource abstraction over RDF data
3. **Delta Resources**: Command pattern for tracking changes with optimistic concurrency
4. **Multi-Store Strategy**: Unified API supporting multiple RDF backends (TDB2, Virtuoso, in-memory)
5. **Conversion Pipeline**: Configurable JSON↔RDF transformation engine
6. **Graph-Aware Operations**: Full support for named graphs and multi-graph operations

### Key Interfaces

- `AccessService`: Main entry point for data access operations
- `TypedResource`: Represents RDF resources with application profile constraints
- `DeltaResource`: Handles resource mutations and change tracking
- `RdfStoreService`: Abstraction over SPARQL-capable RDF stores
- `ApplicationProfile`: Schema definition and validation system

## Testing

The project uses JUnit 5 with Spring Boot Test. Each module has comprehensive test coverage.

### Test Structure
- Unit tests: `src/test/java`
- Test resources: `src/test/resources`
- Integration tests use `@SpringBootTest`
- Some modules use embedded Elasticsearch and in-memory RDF stores for testing

### Running Tests
```bash
# All tests
./gradlew test

# Specific module tests
./gradlew :cube:test

# Test with specific profile
./gradlew test -Dspring.profiles.active=test
```

## Module Dependencies

The dependency graph flows from foundation → data access → business logic → infrastructure:

```
access ← service ← security, action-logger
  ↑
cube ← elastic-components
  ↑
application-profile* ← application-profile-owl, application-profile-shacl
  ↑
triplestore* ← triplestore-pool-api, triplestore-tdb-pool
  ↑
cogni-core, cogni-libs, cogni-sem
```

## Development Guidelines

### Working with Application Profiles
- Application profiles are JSON files that define the semantic schema
- They drive all TypedResource operations and validation
- Located in `src/main/resources` or `src/test/resources`
- Use CURIE notation for compact URI representation

### Working with RDF Data
- Use `AccessService` for high-level operations
- Use `TypedResource` for strongly-typed resource access
- Use `DeltaResource` for mutations
- Leverage `ConversionProfile` for JSON↔RDF transformations

### SPARQL Operations
- Use `SpelService` for template-based SPARQL with parameter binding
- Template files use `.sparql.spel` extension
- Support for both SELECT and CONSTRUCT queries

### Configuration
- Spring profiles control different deployment scenarios
- Use `@ConditionalOnProperty` for optional features
- Configuration classes use `@Import` for cross-module dependencies

## Key Files and Locations

- `/build.gradle`: Root build configuration with all module dependencies
- `/settings.gradle`: Module inclusion configuration
- `/lombok.config`: Lombok configuration
- Each module follows standard Maven directory structure
- Application profiles typically in `src/main/resources/*.ap.json`
- SPARQL templates in `src/main/resources/**/*.sparql.spel`