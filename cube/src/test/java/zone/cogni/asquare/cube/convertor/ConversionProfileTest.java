package zone.cogni.asquare.cube.convertor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.cube.convertor.json.ApplicationProfileToCompactConversionProfile;
import zone.cogni.asquare.cube.convertor.json.ApplicationProfileToConversionProfile;
import zone.cogni.asquare.cube.convertor.json.CompactConversionProfile;
import zone.cogni.asquare.cube.convertor.json.CompactConversionProfileToConversionProfile;
import zone.cogni.asquare.cube.convertor.json.ConversionProfile;
import zone.cogni.asquare.cube.jsondiff.Configuration;
import zone.cogni.asquare.cube.jsondiff.Difference;
import zone.cogni.asquare.cube.jsondiff.JsonDiffCalculator;
import zone.cogni.asquare.cube.jsondiff.JsonDiffInput;
import zone.cogni.asquare.cube.jsondiff.SameFieldJsonSorter;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.in;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


@SpringBootTest(classes = ConversionProfileTestConfig.class)
public class ConversionProfileTest {

  @Autowired
  private ApplicationProfile draftApplicationProfile;

  @Test
  public void read_person_conversion_profile() throws IOException {
    // given
    ClassPathResource resource = new ClassPathResource("convertor/person-conversion-profile.json");

    // when
    CompactConversionProfile conversionModel = CompactConversionProfile.read(resource);

    // then
    assertThat(conversionModel.getTypes()).hasSize(1);

    CompactConversionProfile.Type person = conversionModel.getTypes().get(0);
    assertThat(person.getId()).isEqualTo("Person");
    assertThat(person.getType()).isEqualTo("http://demo.com/person/model#Person");
    assertThat(person.getAttributes()).hasSize(6);

    CompactConversionProfile.Attribute name = person.getById("name");
    assertThat(name.getId()).isEqualTo("name");
    assertThat(name.getProperty()).isEqualTo("http://demo.com/person/model#name");
    assertThat(name.getType()).isEqualTo(CompactConversionProfile.Attribute.Type.datatype);
    assertTrue(name.isSingle());

    CompactConversionProfile.Attribute hasChild = person.getById("hasChild");
    assertThat(hasChild.getId()).isEqualTo("hasChild");
    assertThat(hasChild.getProperty()).isEqualTo("http://demo.com/person/model#hasChild");
    assertThat(hasChild.getType()).isEqualTo(CompactConversionProfile.Attribute.Type.object);
    assertFalse(hasChild.isSingle());

  }

  @Test
  public void read_animal_conversion_profile() throws IOException {
    // given
    InputStream input = new ClassPathResource("convertor/animal-conversion-profile.json").getInputStream();

    // when
    CompactConversionProfile conversionModel = new ObjectMapper().readValue(input, CompactConversionProfile.class);

    // then
    assertThat(conversionModel.getTypes()).hasSize(3);

    CompactConversionProfile.Type animal = conversionModel.getById("Animal");
    assertThat(animal.getAttributes()).hasSize(1);
    assertThat(animal.getSuperClasses()).isEmpty();

    CompactConversionProfile.Type dog = conversionModel.getById("Dog");
    assertThat(dog.getAttributes()).hasSize(1);
    assertThat(dog.getSuperClasses()).hasSize(1);
    assertThat(dog.getSuperClasses()).contains("Mammal");
  }

  @Test
  public void expanded_animal_conversion_profile() throws IOException {
    // given
    InputStream input = new ClassPathResource("convertor/animal-conversion-profile.json").getInputStream();
    CompactConversionProfile conversionModel = new ObjectMapper().readValue(input, CompactConversionProfile.class);

    // when
    ConversionProfile conversionProfile = new CompactConversionProfileToConversionProfile().apply(conversionModel);

    // then
    assertThat(conversionProfile.getTypes()).hasSize(3);

    ConversionProfile.Type mammal = conversionProfile.getTypeFromClassId("Mammal");
    assertThat(mammal.getClassIds()).hasSize(2);
    assertThat(mammal.getClassIds()).containsExactly("Mammal", "Animal");
    assertThat(mammal.getAttributes()).hasSize(2);
    assertThat(mammal.getAttributes()).extracting("attributeId")
                                      .contains("name", "hasHair");


    ConversionProfile.Type dog = conversionProfile.getTypeFromClassId("Dog");
    assertThat(dog.getClassIds()).hasSize(3);
    assertThat(dog.getClassIds()).containsExactly("Mammal", "Animal", "Dog");
    assertThat(dog.getAttributes()).hasSize(3);
    assertThat(dog.getAttributes()).extracting("attributeId")
                                   .contains("name", "hasHair", "barks");
  }

  @Test
  public void compare_conversion_profiles() throws IOException {
    // given
    JsonNode directConversion = getDirectConversion();
    JsonNode phasedConversion = getPhasedConversion();

    System.out.println(directConversion);
    System.out.println(phasedConversion);

    // when
    JsonDiffInput input = new JsonDiffInput();
    input.setId("draft-application-profile");
    input.setFrom(() -> directConversion);
    input.setTo(() -> phasedConversion);

    Configuration configuration = new Configuration();
    configuration.setJsonSorter(new SameFieldJsonSorter("rootClassId", "attributeId"));

    List<Difference> differences = new JsonDiffCalculator(configuration).apply(input);

    // then
    assertThat(differences).isEmpty();

  }

  private JsonNode getPhasedConversion() throws IOException {
    ConversionProfile phasedConversion =
            new ApplicationProfileToCompactConversionProfile()
                    .andThen(new CompactConversionProfileToConversionProfile())
                    .apply(draftApplicationProfile);

    return getJsonNode(phasedConversion);
  }

  private JsonNode getDirectConversion() throws IOException {
    ConversionProfile directConversion =
            new ApplicationProfileToConversionProfile()
                    .apply(draftApplicationProfile);

    return getJsonNode(directConversion);
  }

  private JsonNode getJsonNode(ConversionProfile phasedConversion) throws IOException {
    return new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(phasedConversion));
  }
}
