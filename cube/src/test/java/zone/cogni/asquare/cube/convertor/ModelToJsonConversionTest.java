package zone.cogni.asquare.cube.convertor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamSource;
import zone.cogni.asquare.cube.convertor.ModelToJsonConversion.Configuration.ModelType;
import zone.cogni.asquare.cube.convertor.json.ConversionProfile;
import zone.cogni.libs.jena.utils.JenaUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static zone.cogni.asquare.cube.convertor.ModelToJsonConversion.Configuration.JsonType;

public class ModelToJsonConversionTest {

  @Test
  public void single_type_animal() {
    // given
    Model animalModel = JenaUtils.read(new ClassPathResource("convertor/animal-data-octopus.ttl"));
    ModelToJsonConversion conversion = getAnimalConversion(getAllTypesConfiguration());

    // when
    ObjectNode json = conversion.apply(animalModel, "http://demo.com/data#octo");

    // then
    JsonNode type = navigate(json, "data", "type");
    assertTrue(type.isTextual());
    assertThat(type.textValue()).isEqualTo("Animal");
    System.out.println("json = \n" + json);
  }

  @Test
  public void single_type_animal_unknown_field() {
    // given
    Model animalModel = JenaUtils.read(new ClassPathResource("convertor/animal-data-octopus-unknown-field.ttl"));
    ModelToJsonConversion conversion = getAnimalConversion(getAllTypesConfiguration());

    // when
    ObjectNode json = conversion.apply(animalModel, "http://demo.com/data#octo");
    System.out.println("json = \n" + json);

    // then
    JsonNode attributes = navigate(json, "data", "attributes");
    assertTrue(attributes.isObject());
    assertTrue(attributes.has("name"));
    assertFalse(attributes.has("unknown"));
  }

  @Test
  public void single_type_dog_ok() {
    // given
    Model animalModel = JenaUtils.read(new ClassPathResource("convertor/animal-data-single-type-dog.ttl"));
    ModelToJsonConversion conversion = getAnimalConversion(getRootTypeConfiguration());

    // when
    ObjectNode json = conversion.apply(animalModel, "http://demo.com/data#fido");
    System.out.println("json = \n" + json);

    // then
    JsonNode type = navigate(json, "data", "type");
    assertTrue(type.isTextual());
    assertThat(type.textValue()).isEqualTo("Dog");
  }

  @Test
  public void single_type_dog_not_ok() {
    // given
    Model animalModel = JenaUtils.read(new ClassPathResource("convertor/animal-data-single-type-dog.ttl"));
    ModelToJsonConversion conversion = getAnimalConversion(getAllTypesConfiguration());

    // when
    Exception exception = Assertions.assertThrows(RuntimeException.class, () -> {
      conversion.apply(animalModel, "http://demo.com/data#fido");
    });

    // then
    assertThat(exception.getMessage()).contains("expecting exactly one type for input");
  }

  @Test
  public void wrong_type_dog_root_configuration() {
    // given
    Model animalModel = JenaUtils.read(new ClassPathResource("convertor/animal-data-wrong-type-dog.ttl"));
    ModelToJsonConversion conversion = getAnimalConversion(getRootTypeConfiguration());

    // when
    Exception exception = Assertions.assertThrows(RuntimeException.class, () -> {
      conversion.apply(animalModel, "http://demo.com/data#fido");
    });

    // then
    assertThat(exception.getMessage()).contains("expecting exactly one type, found");
  }

  @Test
  public void wrong_type_dog_all_types_configuration() {
    // given
    Model animalModel = JenaUtils.read(new ClassPathResource("convertor/animal-data-wrong-type-dog.ttl"));
    ModelToJsonConversion conversion = getAnimalConversion(getAllTypesConfiguration());

    // when
    Exception exception = Assertions.assertThrows(RuntimeException.class, () -> {
      conversion.apply(animalModel, "http://demo.com/data#fido");
    });

    // then
    assertThat(exception.getMessage()).contains("expecting exactly one type for input");
  }


  @Test
  public void multi_type_dog_ok() {
    // given
    Model animalModel = JenaUtils.read(new ClassPathResource("convertor/animal-data-multi-type-dog.ttl"));
    ModelToJsonConversion conversion = getAnimalConversion(getAllTypesConfiguration());

    // when
    ObjectNode json = conversion.apply(animalModel, "http://demo.com/data#fido");
    System.out.println("json = \n" + json);

    // then
    JsonNode type = navigate(json, "data", "type");
    assertTrue(type.isArray());
    assertThat(getElements(type)).contains("Animal", "Mammal", "Dog");
  }

  @Test
  public void multi_date_homer_ok() {
    // given
    Model homerModel = JenaUtils.read(new ClassPathResource("convertor/person-data-homer-multi-date.ttl"));
    ModelToJsonConversion conversion = getPersonConversion(getAllTypesConfiguration());

    // when
    ObjectNode json = conversion.apply(homerModel, "http://demo.com/data#homer");
    System.out.println("json = \n" + json);

    // then
    JsonNode hasDate = navigate(json, "data", "attributes", "hasDate");
    assertTrue(hasDate.has("xsd:date"));

    JsonNode dates = hasDate.get("xsd:date");
    assertThat(getElements(dates)).contains("2021-04-29", "2021-05-29", "2021-06-29");
  }

  @Test
  public void any_uri_homer_ok() {
    // given
    Model homerModel = JenaUtils.read(new ClassPathResource("convertor/person-data-homer-any-uri.ttl"));
    ModelToJsonConversion conversion = getPersonConversion(getAllTypesConfiguration());

    // when
    ObjectNode json = conversion.apply(homerModel, "http://demo.com/data#homer");
    System.out.println("json = \n" + json);

    // then
    JsonNode website = navigate(json, "data", "attributes", "website");
    assertTrue(website.has("xsd:anyURI"));

    JsonNode uri = website.get("xsd:anyURI");
    assertThat(getElements(uri)).contains("http://demo.com/homer/simpson");
  }

  @Test
  public void any_datatype_homer_ok() {
    // given
    Model homerModel = JenaUtils.read(new ClassPathResource("convertor/person-data-homer-any-datatype.ttl"));
    ModelToJsonConversion conversion = getPersonConversion(getAllTypesConfiguration());

    // when
    ObjectNode json = conversion.apply(homerModel, "http://demo.com/data#homer");
    System.out.println("json = \n" + json);

    // then
    JsonNode id = navigate(json, "data", "attributes", "hasId");
    assertTrue(id.has("http://demo.com/data-type/id-type"));

    JsonNode value = id.get("http://demo.com/data-type/id-type");
    assertTrue(value.isTextual());
    assertThat(value.asText()).isEqualTo("123.456");
  }

  @Test
  public void rdfs_resources_homer_ok() {
    // given
    Model homerModel = JenaUtils.read(new ClassPathResource("convertor/person-data-homer-resources.ttl"));
    ModelToJsonConversion conversion = getPersonConversion(getAllTypesConfiguration());

    // when
    ObjectNode json = conversion.apply(homerModel, "http://demo.com/data#homer");
    System.out.println("json = \n" + json);

    // then
    JsonNode websites = navigate(json, "data", "attributes", "website", "rdfs:Resource");

    assertThat(websites.size()).isEqualTo(2);
    assertThat(getElements(websites)).contains("http://thesimpsons.com", "http://fanwiki.com/simpsons");
  }


  @Test
  public void profile_type_person_ok() {
    // given
    Model animalModel = JenaUtils.read(new ClassPathResource("convertor/person-data-homer-multi-type.ttl"));
    ModelToJsonConversion conversion = getPersonConversion(getProfileTypeConfiguration());

    // when
    ObjectNode json = conversion.apply(animalModel, "http://demo.com/data#homer");

    // then
    JsonNode type = navigate(json, "data", "type");
    assertTrue(type.isTextual());
    assertThat(type.textValue()).isEqualTo("Person");
    System.out.println("json = \n" + json);
  }

  @Test
  public void inverse_attribute_ok() {
    // given
    Model animalPersonModel = JenaUtils.read(new ClassPathResource("convertor/animal-person-inverse.ttl"));
    ModelToJsonConversion.Configuration configuration = getRootTypeConfiguration();
    configuration.setInverseAttributesSupported(true);
    ModelToJsonConversion conversion = getAnimalPersonConversion(configuration);

    // when
    ObjectNode json = conversion.apply(animalPersonModel, "http://demo.com/data#santaslittlehelper");

    // then
    JsonNode type = navigate(json, "data", "references", "ownedBy");
    assertTrue(type.isTextual());
    assertThat(type.textValue()).isEqualTo("http://demo.com/data#homer");
    System.out.println("json = \n" + json);
  }

  private JsonNode navigate(ObjectNode json, String... path) {
    JsonNode result = json;
    for (int i = 0; i < path.length; i++) {
      result = result.get(path[i]);
    }
    return result;
  }

  private Collection<String> getElements(JsonNode typeNode) {
    if (typeNode.isTextual()) Stream.of(typeNode.textValue()).collect(Collectors.toSet());

    if (typeNode.isArray()) {
      ArrayNode arrayNode = (ArrayNode) typeNode;

      Set<String> result = new HashSet<>();
      arrayNode.elements().forEachRemaining(e -> result.add(e.textValue()));
      return result;
    }

    throw new RuntimeException("should never get here");
  }

  private ModelToJsonConversion getAnimalConversion(ModelToJsonConversion.Configuration configuration) {
    InputStreamSource input = new ClassPathResource("convertor/animal-conversion-profile.json");
    return new ModelToJsonConversion(configuration, ConversionProfile.read(input));
  }

  private ModelToJsonConversion getPersonConversion(ModelToJsonConversion.Configuration configuration) {
    InputStreamSource input = new ClassPathResource("convertor/person-conversion-profile.json");
    return new ModelToJsonConversion(configuration, ConversionProfile.read(input));
  }

  private ModelToJsonConversion getAnimalPersonConversion(ModelToJsonConversion.Configuration configuration) {
    InputStreamSource input = new ClassPathResource("convertor/animal-person-conversion-profile.json");
    return new ModelToJsonConversion(configuration, ConversionProfile.read(input));
  }

  private ModelToJsonConversion.Configuration getAllTypesConfiguration() {
    ModelToJsonConversion.Configuration configuration = new ModelToJsonConversion.Configuration();
    configuration.setLogIssues(true);
    configuration.setModelType(ModelType.ALL);
    configuration.setJsonType(JsonType.ALL);
    return configuration;
  }

  private ModelToJsonConversion.Configuration getProfileTypeConfiguration() {
    ModelToJsonConversion.Configuration configuration = new ModelToJsonConversion.Configuration();
    configuration.setLogIssues(true);
    configuration.setModelType(ModelType.PROFILE);
    configuration.setJsonType(JsonType.ALL);
    return configuration;
  }

  private ModelToJsonConversion.Configuration getRootTypeConfiguration() {
    ModelToJsonConversion.Configuration configuration = new ModelToJsonConversion.Configuration();
    configuration.setLogIssues(true);
    configuration.setModelType(ModelType.ROOT);
    configuration.setJsonType(JsonType.ROOT);
    return configuration;
  }
}
