package zone.cogni.asquare.cube.convertor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.jsonldjava.utils.JsonUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcConfiguration;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcService;
import zone.cogni.asquare.cube.convertor.json.ConversionProfile;
import zone.cogni.libs.jena.utils.JenaUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;


class JsonToModelConversionTest {

  private static final String AnimalConversionProfile = "convertor/animal-conversion-profile.json";
  private static final String AnimalPersonConversionProfile = "convertor/animal-person-conversion-profile.json";
  private static final String PersonConversionProfile = "convertor/person-conversion-profile.json";
  private static final String PersonConversionProfileCurie = "convertor/person-conversion-profile-curie.json";

  private static final ModelToJsonConversion.Configuration DefaultConfiguration = new ModelToJsonConversion.Configuration();
  private static final ModelToJsonConversion.Configuration RootOnlyConfiguration = new ModelToJsonConversion.Configuration();

  static {
    RootOnlyConfiguration.setModelType(ModelToJsonConversion.Configuration.ModelType.ROOT);
  }

  private static final ModelToJsonConversion.Configuration ContextConfiguration = new ModelToJsonConversion.Configuration();

  static {
    ContextConfiguration.setContextEnabled(true);
  }


  @Test
  public void basic_properties_and_relations() throws IOException {
    // given
    Model homerModel = JenaUtils.read(new ClassPathResource("convertor/person-data-homer.ttl"));
    ObjectNode jsonNode = getObjectNode("convertor/person-data-homer.json");
    JsonToModelConversion conversion = getConversionProfile(PersonConversionProfile, DefaultConfiguration);

    // when
    Model newModel = conversion.apply(jsonNode);
    newModel.write(System.out, "ttl");

    // then
    assertThat(newModel.size()).isEqualTo(homerModel.size());
  }

  @Test
  public void inheritance_multi_type() throws IOException {
    // given
    Model animalModel = JenaUtils.read(new ClassPathResource("convertor/animal-data-multi-type-dog.ttl"));
    ObjectNode jsonNode = getObjectNode("convertor/animal-data-multi-type-dog.json");
    JsonToModelConversion conversion = getConversionProfile(AnimalConversionProfile, DefaultConfiguration);

    // when
    Model newModel = conversion.apply(jsonNode);
    newModel.write(System.out, "ttl");

    // then
    assertThat(newModel.size()).isEqualTo(animalModel.size());
  }

  @Test
  public void inheritance_root_type_array() throws IOException {
    // given
    Model animalModel = JenaUtils.read(new ClassPathResource("convertor/animal-data-single-type-dog.ttl"));
    ObjectNode jsonNode = getObjectNode("convertor/animal-data-single-type-dog-array.json");
    JsonToModelConversion conversion = getConversionProfile(AnimalConversionProfile, RootOnlyConfiguration);

    // when
    Model newModel = conversion.apply(jsonNode);
    newModel.write(System.out, "ttl");

    // then
    assertThat(newModel.size()).isEqualTo(animalModel.size());
  }

  @Test
  public void inheritance_root_type_object() throws IOException {
    // given
    Model animalModel = JenaUtils.read(new ClassPathResource("convertor/animal-data-single-type-dog.ttl"));
    ObjectNode jsonNode = getObjectNode("convertor/animal-data-single-type-dog-object.json");
    JsonToModelConversion conversion = getConversionProfile(AnimalConversionProfile, RootOnlyConfiguration);

    // when
    Model newModel = conversion.apply(jsonNode);
    newModel.write(System.out, "ttl");

    // then
    assertThat(newModel.size()).isEqualTo(animalModel.size());
  }

  @Test
  public void inverse_attribute_ok() throws IOException {
    // given
    Model fromFile = JenaUtils.read(new ClassPathResource("convertor/animal-person-inverse.ttl"));
    ObjectNode jsonNode = getObjectNode("convertor/animal-person-inverse.json");
    JsonToModelConversion conversion = getConversionProfile(AnimalPersonConversionProfile, DefaultConfiguration);

    // when
    Model newModel = conversion.apply(jsonNode);
    newModel.write(System.out, "ttl");

    // then
    Resource s = ResourceFactory.createResource("http://demo.com/data#homer");
    Property p = ResourceFactory.createProperty("http://demo.com/person/model#owns");
    Resource o = ResourceFactory.createResource("http://demo.com/data#santaslittlehelper");
    assertThat(newModel.contains(s, p, o)).isTrue();
    assertThat(newModel.size()).isEqualTo(fromFile.size());
  }

//  @Test
  public void context_test() throws IOException {
    // given
    ObjectNode jsonNode = getObjectNode("convertor/person-data-homer-curie.json");
    JsonToModelConversion conversion = getConversionProfile(PersonConversionProfileCurie,
                                                            ContextConfiguration);

    // when
    Model newModel = conversion.apply(jsonNode);
    newModel.write(System.out, "ttl");

    // then
    assertThat(newModel.size()).isEqualTo(11);
  }

  private ObjectNode getObjectNode(String path) throws IOException {
    Object json = JsonUtils.fromInputStream(new ClassPathResource(path).getInputStream());
    return new ObjectMapper().valueToTree(json);
  }

  private JsonToModelConversion getConversionProfile(String conversionProfileResource,
                                                     ModelToJsonConversion.Configuration configuration) {
    ConversionProfile conversionProfile = ConversionProfile.read(new ClassPathResource(conversionProfileResource));
    return new JsonToModelConversion(getPrefixCcService(), conversionProfile, configuration);
  }

  private PrefixCcService getPrefixCcService() {
    PrefixCcConfiguration prefixCcConfiguration = new PrefixCcConfiguration();
    return new PrefixCcService(prefixCcConfiguration);
  }

}