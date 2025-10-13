# asquareroot

**Test Infrastructure Library for asquare**

## Purpose

`asquareroot` provides test utilities and embedded services for testing asquare applications:

- **Embedded Elasticsearch 7.5.2** - In-memory ES instance for integration tests
- **In-memory RDF store** - Testing with embedded triple stores
- **Spring Boot test annotations** - `@EnableEmbeddedElastic`, `@EnableRealEnvironment`, etc.
- **JUnit 4 & 5 support** - Test environment management

## Important: Test-Only Dependency

**This module is designed exclusively for testing and should NEVER be used in production code.**

### Maven/Gradle Usage

When adding asquareroot as a dependency, **always** use test scope:

```gradle
// Gradle
dependencies {
    testImplementation 'zone.cogni.asquare:asquareroot:${version}'
}
```

```xml
<!-- Maven -->
<dependency>
    <groupId>zone.cogni.asquare</groupId>
    <artifactId>asquareroot</artifactId>
    <version>${asquare.version}</version>
    <scope>test</scope>
</dependency>
```

## Licensing

This module depends on **Elasticsearch 7.5.2** which is licensed under **Apache License 2.0** (versions before 7.11 used Apache 2.0; later versions changed to Elastic License).

Since asquareroot uses `implementation` scope for Elasticsearch dependencies, the ES classes are not transitively exposed to your project.

## Quick Start

### Using Embedded Elasticsearch

```java
@SpringBootTest
@EnableEmbeddedElastic
public class MyIntegrationTest {

    @Autowired
    private Elasticsearch7Store elasticsearchStore;

    @Test
    public void testWithEmbeddedES() {
        // Your test code here
    }
}
```

### Using JUnit 5

```java
@ExtendWith(A2EmbeddedEnvironmentsJUnit5.class)
@EnableEmbeddedElastic
public class MyTest {
    // Test methods
}
```

### Using JUnit 4

```java
@RunWith(A2EmbeddedEnvironmentsJUnit4.class)
@EnableEmbeddedElastic
public class MyTest {
    // Test methods
}
```

## Available Annotations

- `@EnableEmbeddedElastic` - Starts embedded Elasticsearch instance
- `@EnableRealEnvironment` - Uses real external services (not embedded)
- `@EnableRelaxedVirtuosoSelectSimulation` - Relaxed SPARQL validation

## Architecture

asquareroot automatically intercepts Spring beans configured to connect to external services and replaces them with embedded instances:

- `HttpElasticsearch7Store` → `EmbeddedElasticsearch7Store`
- External RDF stores → `InMemoryRdfStoreService`
- `SparqlService` → `JenaModelSparqlService`

This happens transparently through Spring's bean post-processing mechanism.

## Requirements

- Java 17+
- Spring Boot 3.5+
- JUnit 4.x or 5.x

## Notes

- Embedded Elasticsearch uses temporary directories that are cleaned up after tests
- Each test class gets its own isolated ES instance by default
- The embedded ES node runs on `127.0.0.1:9200` (configurable)
