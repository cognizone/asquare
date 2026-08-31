package zone.cogni.asquareroot.elastic;

import org.elasticsearch.common.xcontent.XContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Elasticsearch 7.5.2 x-content runs on Jackson 2, while the main line is Jackson 3 (tools.jackson).
 * Since spring-boot-starter-json stopped supplying com.fasterxml.jackson.core, the Jackson 2
 * artifacts have to be declared explicitly in build.gradle.
 * <p>
 * Without them every call below fails with
 * NoClassDefFoundError: com/fasterxml/jackson/core/JsonFactory.
 * <p>
 * asquare has no direct XContent call, but EmbeddedElasticsearch7Store reaches it through the
 * Elasticsearch client - createIndex does IndexRequest.source(settings, Requests.INDEX_CONTENT_TYPE),
 * which is XContentType.JSON. The only test covering that path, EmbeddedElasticTest, is disabled,
 * so without the check below the build does not notice the missing Jackson 2 dependencies.
 */
class XContentClasspathTest {

  @Test
  void every_xcontent_type_has_an_implementation() {
    assertAll(Arrays.stream(XContentType.values())
                    .map(type -> (Executable) () -> assertNotNull(type.xContent(),
                                                                  "no XContent implementation for " + type)));
  }
}
