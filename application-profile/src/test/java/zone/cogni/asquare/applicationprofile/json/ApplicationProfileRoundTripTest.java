package zone.cogni.asquare.applicationprofile.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that an ApplicationProfile can be serialized to JSON and deserialized
 * back to an equivalent ApplicationProfile (round-trip fidelity).
 * Tests the ApplicationProfileSerializer and ApplicationProfileDeserializer together.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = ApplicationProfileConfig.class)
class ApplicationProfileRoundTripTest {

  @Autowired
  private ApplicationProfileConfig applicationProfileConfig;

  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Serialize an ApplicationProfile to JSON bytes, then deserialize back.
   */
  private ApplicationProfile roundTrip(ApplicationProfile original) throws IOException {
    JsonNode serialized = applicationProfileConfig.getSerializer()
                                                  .apply(original.getApplicationProfileDef());
    byte[] json = objectMapper.writeValueAsBytes(serialized);
    return applicationProfileConfig.getDeserializer().apply(new ByteArrayResource(json));
  }

  // -------------------------------------------------------------------------
  // Simple AP (no imports) — full round-trip
  // -------------------------------------------------------------------------

  @Test
  void simpleAP_typeCount_survivesRoundTrip() throws IOException {
    Resource resource = new ClassPathResource("model/basic/imported.ap.json");
    ApplicationProfile original = applicationProfileConfig.getDeserializer().apply(resource);

    ApplicationProfile roundTripped = roundTrip(original);

    assertEquals(original.getTypes().size(), roundTripped.getTypes().size());
  }

  @Test
  void simpleAP_typeNames_survivesRoundTrip() throws IOException {
    Resource resource = new ClassPathResource("model/basic/imported.ap.json");
    ApplicationProfile original = applicationProfileConfig.getDeserializer().apply(resource);

    ApplicationProfile roundTripped = roundTrip(original);

    Set<String> originalNames = original.getTypes().keySet();
    Set<String> roundTrippedNames = roundTripped.getTypes().keySet();

    assertEquals(originalNames, roundTrippedNames);
  }

  @Test
  void simpleAP_profileUri_survivesRoundTrip() throws IOException {
    Resource resource = new ClassPathResource("model/basic/imported.ap.json");
    ApplicationProfile original = applicationProfileConfig.getDeserializer().apply(resource);

    ApplicationProfile roundTripped = roundTrip(original);

    assertEquals(
        original.getApplicationProfileDef().getUri(),
        roundTripped.getApplicationProfileDef().getUri());
  }

  // -------------------------------------------------------------------------
  // AP with attributes — attribute URIs survive serialization
  // -------------------------------------------------------------------------

  @Test
  void attributeUris_survivesRoundTrip() throws IOException {
    // importing.ap.json defines Car with licensePlate and owner attributes
    Resource resource = new ClassPathResource("model/basic/importing.ap.json");
    ApplicationProfile original = applicationProfileConfig.getDeserializer().apply(resource);

    // Serialize only the root definition (Car type) — imports are not serialized
    JsonNode serialized = applicationProfileConfig.getSerializer()
                                                  .apply(original.getApplicationProfileDef());
    byte[] json = objectMapper.writeValueAsBytes(serialized);
    ApplicationProfile carOnly = applicationProfileConfig.getDeserializer()
                                                         .apply(new ByteArrayResource(json));

    assertTrue(carOnly.hasType("Car"), "Car type must survive round-trip");

    ApplicationProfile.Type car = carOnly.getType("Car");
    assertNotNull(car.getAttribute("licensePlate"), "licensePlate attribute must be present");
    assertNotNull(car.getAttribute("owner"), "owner attribute must be present");

    assertEquals("http://demo.com/onto/licensePlate",
                 car.getAttribute("licensePlate").getUri());
    assertEquals("http://demo.com/onto/owner",
                 car.getAttribute("owner").getUri());
  }

  // -------------------------------------------------------------------------
  // Serialized JSON structure
  // -------------------------------------------------------------------------

  @Test
  void serializedJson_containsUriField() {
    Resource resource = new ClassPathResource("model/basic/imported.ap.json");
    ApplicationProfile original = applicationProfileConfig.getDeserializer().apply(resource);

    JsonNode serialized = applicationProfileConfig.getSerializer()
                                                  .apply(original.getApplicationProfileDef());

    assertTrue(serialized.has("uri"), "Serialized JSON must contain 'uri' field");
    assertEquals("http://demo.com/", serialized.get("uri").asText());
  }

  @Test
  void serializedJson_containsTypeNames() {
    Resource resource = new ClassPathResource("model/basic/imported.ap.json");
    ApplicationProfile original = applicationProfileConfig.getDeserializer().apply(resource);

    JsonNode serialized = applicationProfileConfig.getSerializer()
                                                  .apply(original.getApplicationProfileDef());

    assertTrue(serialized.has("Organization"),
               "Serialized JSON must contain 'Organization' type");
    assertTrue(serialized.has("RegisteredOrganization"),
               "Serialized JSON must contain 'RegisteredOrganization' type");
  }
}
