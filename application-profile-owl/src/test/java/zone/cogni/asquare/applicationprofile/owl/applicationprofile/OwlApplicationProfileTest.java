package zone.cogni.asquare.applicationprofile.owl.applicationprofile;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import zone.cogni.asquare.applicationprofile.json.ApplicationProfileDeserializer;
import zone.cogni.asquare.applicationprofile.json.ApplicationProfileSerializer;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.model.basic.def.ApplicationProfileDef;
import zone.cogni.asquare.applicationprofile.owl.ap2owl.ApplicationProfile2Owl;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.SquareOwl2ApplicationProfile;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcConfiguration;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcService;

import static org.junit.jupiter.api.Assertions.assertEquals;


@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = PrefixCcService.class)
@Disabled
public class OwlApplicationProfileTest {

  @Autowired
  private PrefixCcService prefixCcService;

  public OwlApplicationProfileTest() {
  }

  @Test
  public void test_uri() {
    convertAndCompare("uri.ap.json");
  }

  @Test
  public void test_extra() {
    convertAndCompare("extra.ap.json");
  }

  @Test
  public void test_type() {
    convertAndCompare("type.ap.json");
  }

  @Test
  public void test_extra_on_type() {
    convertAndCompare("extra-on-type.ap.json");
  }

  @Test
  public void test_attribute() {
    convertAndCompare("attribute.ap.json");
  }

  @Test
  public void test_extra_on_attribute() {
    convertAndCompare("extra-on-attribute.ap.json");
  }

  @Test
  public void test_min_cardinality_on_attribute() {
    convertAndCompare("min-cardinality-on-attribute.ap.json");
  }

  @Test
  public void test_max_cardinality_on_attribute() {
    convertAndCompare("max-cardinality-on-attribute.ap.json");
  }

  @Test
  public void test_cardinality_on_attribute() {
    convertAndCompare("cardinality-on-attribute.ap.json");
  }

  @Test
  public void test_range_on_attribute() {
    convertAndCompare("range-on-attribute.ap.json");
  }

  @Test
  public void test_or_range_on_attribute() {
    convertAndCompare("or-range-on-attribute.ap.json");
  }

  @Test
  public void test_and_range_on_attribute() {
    convertAndCompare("and-range-on-attribute.ap.json");
  }

  //  @Test
  public void test_not_range_on_attribute() {
    convertAndCompare("not-range-on-attribute.ap.json");
  }

  @Test
  public void test_classid_range_on_attribute() {
    convertAndCompare("classid-range-on-attribute.ap.json");
  }

  @Test
  public void test_and_or_range_on_attribute() {
    convertAndCompare("and-or-range-on-attribute.ap.json");
  }

  //  @Test
  public void test_and_not_or_range_on_attribute() {
    convertAndCompare("and-not-or-range-on-attribute.ap.json");
  }

  @Test
  public void test_not_range_on_type() {
    convertAndCompare("not-range-on-type.ap.json");
  }

  private void convertAndCompare(String fileName) {
    ClassPathResource jsonResource = new ClassPathResource("applicationprofile/" + fileName);
    JsonNode inputJson = ApplicationProfileDeserializer.asJsonNode(jsonResource);

    // read Resource into AP
    ApplicationProfile inputApplicationProfile = new ApplicationProfileDeserializer(new PrefixCcService(new PrefixCcConfiguration())).apply(jsonResource);

    String inputApString = new ApplicationProfileSerializer().apply(inputApplicationProfile.getApplicationProfileDef()).toString();

    System.out.println("inputAp = " + inputApString);
    assertEquals("Resource -> AP conversion failed.", inputJson.toString(), inputApString);

    // convert AP into OWL
    Model owlModel = new ApplicationProfile2Owl()
            .withPrefixCcService(prefixCcService)
            .apply(inputApplicationProfile);

    System.out.println("owlModel.size() = " + owlModel.size());
    owlModel.write(System.out, "TURTLE");

    // convert OWL into AP
    ApplicationProfileDef outputAp = new SquareOwl2ApplicationProfile(prefixCcService)
            .apply(owlModel);

    String outputApString = new ApplicationProfileSerializer().apply(outputAp).toString();
    System.out.println("outputAp = " + outputApString);
    assertEquals("AP -> OWL -> AP conversion failed.", inputApString, outputApString);

  }

}
