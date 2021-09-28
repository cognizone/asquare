package zone.cogni.asquare.service.jsonconversion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vavr.control.Try;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import zone.cogni.asquare.access.ApplicationView;
import zone.cogni.asquare.service.ApplicationViewTestConfig;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = ApplicationViewToJsonTest.Config.class)
public class ApplicationViewToJsonTest {

  @Autowired
  Config config;

  @Test
  public void testLoadSettlement() {

    ObjectNode node = config.getToJson().withDataTypeId("Settlement", true).get();

    ObjectMapper mapper = new ObjectMapper();
    mapper.enable(SerializationFeature.INDENT_OUTPUT);
    String written = Try.of(() -> mapper.writeValueAsString(node)).get();
    System.out.println(written);
    ObjectNode read = (ObjectNode) Try.of(() -> mapper.readTree(written.getBytes(StandardCharsets.UTF_8))).get();

    JsonNode data = read.get("data");

    assertNotNull(data);
    assertTrue(data.isObject());

    assertEquals(4, data.size());

    assertNotNull(data.get("uri"));
    assertEquals(JsonNodeType.STRING, data.get("uri").getNodeType());
    assertEquals("http://dbpedia.org/resource/Němčice_(Prachatice_District)", data.get("uri").asText());

    assertNotNull(data.get("type"));
    assertEquals(JsonNodeType.STRING, data.get("type").getNodeType());
    assertEquals("Settlement", data.get("type").asText());


    JsonNode attributes = data.get("attributes");

    assertNotNull(attributes.get("populationTotal").get("xsd:nonNegativeInteger"));
    assertEquals(JsonNodeType.NUMBER, attributes.get("populationTotal").get("xsd:nonNegativeInteger").getNodeType());
    assertEquals(187, attributes.get("populationTotal").get("xsd:nonNegativeInteger").asInt());

    assertNotNull(attributes.get("demographicsAsOf").get("xsd:date"));
    assertEquals(JsonNodeType.STRING, attributes.get("demographicsAsOf").get("xsd:date").getNodeType());
    assertEquals("2009-04-03", attributes.get("demographicsAsOf").get("xsd:date").asText());

    assertNotNull(attributes.get("lat").get("xsd:float"));
    assertEquals(JsonNodeType.NUMBER, attributes.get("lat").get("xsd:float").getNodeType());
    assertEquals(49.0331, attributes.get("lat").get("xsd:float").asDouble(), 0.0001);

    assertNotNull(attributes.get("name").get("rdf:langString"));
    assertEquals(JsonNodeType.OBJECT, attributes.get("name").get("rdf:langString").getNodeType());
    assertEquals(1, attributes.get("name").get("rdf:langString").size());
    assertNotNull(attributes.get("name").get("rdf:langString").get("en"));
    assertEquals(JsonNodeType.STRING, attributes.get("name").get("rdf:langString").get("en").getNodeType());
    assertEquals("Němčice", attributes.get("name").get("rdf:langString").get("en").asText());

    assertNotNull(attributes.get("abstract"));
    assertEquals(JsonNodeType.OBJECT, attributes.get("abstract").get("rdf:langString").getNodeType());
    assertEquals(6, attributes.get("abstract").get("rdf:langString").size());
    assertNotNull(attributes.get("abstract").get("rdf:langString").get("en"));
    assertEquals(JsonNodeType.STRING, attributes.get("abstract").get("rdf:langString").get("en").getNodeType());
    assertEquals("Němčice is a village and municipality (obec) in Prachatice District in the South Bohemian Region of the Czech Republic. The municipality covers an area of 4.14 square kilometres (1.60 sq mi), and has a population of 187 (as at 28 August 2006). Němčice lies approximately 21 kilometres (13 mi) east of Prachatice, 17 km (11 mi) north-west of České Budějovice, and 118 km (73 mi) south of Prague.",
        attributes.get("abstract").get("rdf:langString").get("en").asText());

    assertNotNull(data.get("references"));
    assertEquals(JsonNodeType.OBJECT, data.get("references").getNodeType());
    assertEquals(1, data.get("references").size());
    assertNotNull(data.get("references").get("country"));
    assertEquals(JsonNodeType.STRING, data.get("references").get("country").getNodeType());
    assertEquals("http://dbpedia.org/resource/Czech_Republic", data.get("references").get("country").asText());

    JsonNode included = read.get("included");

    assertNotNull(included);
    assertEquals(JsonNodeType.ARRAY, included.getNodeType());
    assertEquals(1, included.size());

    JsonNode country = included.get(0);
    assertNotNull(country);
    assertEquals(JsonNodeType.OBJECT, country.getNodeType());
    assertEquals(3, country.size());

    assertNotNull(country.get("uri"));
    assertEquals(JsonNodeType.STRING, country.get("uri").getNodeType());
    assertEquals("http://dbpedia.org/resource/Czech_Republic", country.get("uri").asText());

    assertNotNull(country.get("type"));
    assertEquals(JsonNodeType.STRING, country.get("type").getNodeType());
    assertEquals("Country", country.get("type").asText());


    JsonNode countryAttributes = country.get("attributes");
    assertNotNull(countryAttributes.get("label").get("rdf:langString"));
    assertEquals(JsonNodeType.OBJECT, countryAttributes.get("label").get("rdf:langString").getNodeType());
    assertEquals(12, countryAttributes.get("label").get("rdf:langString").size());
    assertNotNull(countryAttributes.get("label").get("rdf:langString").get("en"));
    assertEquals(JsonNodeType.ARRAY, countryAttributes.get("label").get("rdf:langString").get("en").getNodeType());
    assertNotNull(countryAttributes.get("label").get("rdf:langString").get("en").get(0));
    assertEquals(JsonNodeType.STRING, countryAttributes.get("label").get("rdf:langString").get("en").get(0).getNodeType());
    assertEquals("Czech Republic", countryAttributes.get("label").get("rdf:langString").get("en").get(0).asText());

  }

  @Configuration
  @Import({ApplicationViewTestConfig.class, JsonConversionFactory.class})
  public static class Config {

    private final ApplicationViewTestConfig applicationViewTestConfig;
    private final JsonConversionFactory applicationViewToJsonFactory;

    public Config(ApplicationViewTestConfig applicationViewTestConfig, JsonConversionFactory applicationViewToJsonFactory) {
      this.applicationViewTestConfig = applicationViewTestConfig;
      this.applicationViewToJsonFactory = applicationViewToJsonFactory;
    }

    private ApplicationView getSettlementApplicationView() {
      return applicationViewTestConfig.getApplicationView("jsonconversion/to-json-test.ap.json",
          "jsonconversion/to-json-test.data.ttl");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public ApplicationViewToJson getToJson() {
      return applicationViewToJsonFactory.getApplicationViewToJson()
          .withApplicationViewSupplier(this::getSettlementApplicationView);
    }

  }
}
