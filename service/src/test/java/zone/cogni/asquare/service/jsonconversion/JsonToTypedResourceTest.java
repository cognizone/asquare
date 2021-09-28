package zone.cogni.asquare.service.jsonconversion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import zone.cogni.asquare.access.ApplicationView;
import zone.cogni.asquare.edit.MutableResource;
import zone.cogni.asquare.rdf.BasicRdfValue;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.rdf.TypedResource;
import zone.cogni.asquare.service.ApplicationViewTestConfig;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = JsonToTypedResourceTest.Config.class)
public class JsonToTypedResourceTest {

  @Autowired
  Config config;


  @Test
  public void loadSettlement() {

    ObjectNode json = (ObjectNode) config.getJson("jsonconversion/settlement.json");

    MutableResource newResource = config.getJsonToUpdatableResource().withJsonRoot(json).get().get(0);

    ApplicationProfile.Type settlementType = newResource.getApplicationProfile().getType("Settlement");

    BasicRdfValue pop = newResource.getValue(settlementType.getAttribute("populationTotal"));
    assertNotNull(pop);
    assertEquals(XSDDatatype.XSDnonNegativeInteger.getURI(), pop.getLiteral().getDatatype().getURI());
    assertEquals(187, pop.getLiteral().getInt());

    TypedResource countryResource =  newResource.getValue(settlementType.getAttribute("country"));
    assertNotNull(countryResource);

    ApplicationProfile.Type countryType = newResource.getApplicationProfile().getType("Country");
    List<BasicRdfValue> label = countryResource.getValues(countryType.getAttribute("label"));
    assertEquals(12, label.size());
    assertEquals("Czech Republic",
                 label.stream()
                         .filter(lit -> "en".equals(lit.getLiteral().getLanguage()))
                         .findAny()
                         .get()
                         .getLiteral().getLexicalForm());
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

    public JsonToTypedResource getJsonToUpdatableResource() {
      return applicationViewToJsonFactory.getJsonToUpdatableResource()
              .withApplicationView(getSettlementApplicationView());
    }

    public JsonNode getJson(String path) {

      ClassPathResource jsonResource = new ClassPathResource(path);
      try {
        return new ObjectMapper().readTree(jsonResource.getInputStream());
      }
      catch (IOException e) {
        throw new IllegalStateException(e);
      }

    }

  }
}
