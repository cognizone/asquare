package zone.cogni.asquare.cube.jsondiff;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonDiffTest {

  @Test
  public void switched_array_values() {
    // given
    JsonDiffInput input = getJsonDiffInput("switched_array_values");
    JsonDiffCalculator diffCalculator = getDefaultJsonDiffCalculator();

    // when
    List<Difference> differences = diffCalculator.apply(input);

    // then
    assertThat(differences).isEmpty();
  }

  @Test
  public void items_only_in_source() {
    // given
    JsonDiffInput input = getJsonDiffInput("items_only_source");
    JsonDiffCalculator diffCalculator = getDefaultJsonDiffCalculator();

    // when
    List<Difference> differences = diffCalculator.apply(input);

    // then
    assertThat(differences).hasSize(2);
    assertThat(differences).extracting("type")
                           .contains(Difference.Type.different_array_size, Difference.Type.element_only_in_from);
  }

  @Test
  public void items_only_in_target() {
    // given
    JsonDiffInput input = getJsonDiffInput("items_only_target");
    JsonDiffCalculator diffCalculator = getDefaultJsonDiffCalculator();

    // when
    List<Difference> differences = diffCalculator.apply(input);

    // then
    System.out.println("differences = " + differences);
    assertThat(differences).hasSize(2);
    assertThat(differences).extracting("type")
                           .contains(Difference.Type.different_array_size, Difference.Type.element_only_in_to);
  }


  @Test
  public void different_array_size() {
    // given
    JsonDiffInput input = getJsonDiffInput("different_array_size");
    JsonDiffCalculator diffCalculator = getDefaultJsonDiffCalculator();

    // when
    List<Difference> differences = diffCalculator.apply(input);

    // then
    assertThat(differences).hasSize(2);
    assertThat(differences).extracting("type")
                           .contains(Difference.Type.different_array_size);
  }

  @Test
  public void different_node_types() {
    // given
    JsonDiffInput input = getJsonDiffInput("different_node_types");
    JsonDiffCalculator diffCalculator = getDefaultJsonDiffCalculator();

    // when
    List<Difference> differences = diffCalculator.apply(input);

    // then
    assertThat(differences).hasSize(1);
    assertThat(differences).extracting("type")
                           .contains(Difference.Type.different_node_types);
  }

  @Test
  public void missing_field() {
    // given
    JsonDiffInput input = getJsonDiffInput("missing_field");
    JsonDiffCalculator diffCalculator = getDefaultJsonDiffCalculator();

    // when
    List<Difference> differences = diffCalculator.apply(input);

    // then
    assertThat(differences).hasSize(2);
    assertThat(differences).extracting("type")
                           .contains(Difference.Type.field_only_in_from, Difference.Type.field_only_in_to);

  }

  @Test
  public void wrong_boolean() {
    // given
    JsonDiffInput input = getJsonDiffInput("wrong_boolean");
    JsonDiffCalculator diffCalculator = getDefaultJsonDiffCalculator();

    // when
    List<Difference> differences = diffCalculator.apply(input);

    // then
    assertThat(differences).hasSize(1);
    assertThat(differences.get(0).getType()).isEqualTo(Difference.Type.different_boolean_values);
  }

  @Test
  public void wrong_decimal() {
    // given
    JsonDiffInput input = getJsonDiffInput("wrong_decimal");
    JsonDiffCalculator diffCalculator = getDefaultJsonDiffCalculator();

    // when
    List<Difference> differences = diffCalculator.apply(input);

    // then
    assertThat(differences).hasSize(1);
    assertThat(differences.get(0).getType()).isEqualTo(Difference.Type.different_decimal_values);
  }


  @Test
  public void wrong_integer() {
    // given
    JsonDiffInput input = getJsonDiffInput("wrong_integer");
    JsonDiffCalculator diffCalculator = getDefaultJsonDiffCalculator();

    // when
    List<Difference> differences = diffCalculator.apply(input);

    System.out.println("differences = " + differences);

    // then
    assertThat(differences).hasSize(1);
    assertThat(differences.get(0).getType()).isEqualTo(Difference.Type.different_integer_values);
  }

  private JsonDiffCalculator getDefaultJsonDiffCalculator() {
    Configuration configuration = new Configuration();
    configuration.setPreserveArrayOrder(false);
    return new JsonDiffCalculator(configuration);
  }

  private JsonDiffInput getJsonDiffInput(String filePrefix) {
    JsonDiffInput input = new JsonDiffInput();
    input.setId(filePrefix);
    input.setFrom(getJsonNodeSupplier("jsondiff/" + filePrefix + "_from.json"));
    input.setTo(getJsonNodeSupplier("jsondiff/" + filePrefix + "_to.json"));

    return input;
  }

  @Nonnull
  private Supplier<JsonNode> getJsonNodeSupplier(String fromPath) {
    return () -> {
      try {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(JsonParser.Feature.ALLOW_COMMENTS);
        return objectMapper.readTree(new ClassPathResource(fromPath).getInputStream());
      }
      catch (IOException e) {
        throw new RuntimeException("reading file failed", e);
      }
    };
  }

}
