package zone.cogni.asquare.cube.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import zone.cogni.sem.jena.JenaUtils;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {OperationResultProcessorMergeTestConfig.class})
public class OperationResultProcessorMergeTest {

  private static final String homer = "http://demo.com/data#homer";

  @Autowired
  OperationResultJsonConversion mergedRootConversion;

  @Autowired
  OperationResultProcessor mergedRootOperations;

  @Test
  public void merged_root_operations() {
    // given
    Model model = getHomerModel();

    // when
    Set<String> allPermissions = mergedRootOperations.getOperationRoot().getOperationIds();
    ObjectNode standaloneJson = mergedRootConversion.createStandaloneJson(allPermissions,
                                                                    model,
                                                                    homer,
                                                                    "three");

    System.out.println("standaloneJson = \n" + standaloneJson);

    // then
    assertThat(standaloneJson).isInstanceOf(ObjectNode.class);
    assertThat(standaloneJson.get("id").asText()).isEqualTo("three");
    assertThat(standaloneJson.get("operations")).isInstanceOf(ArrayNode.class);

    JsonNode operation = standaloneJson.get("operations").get(0);
    assertThat(operation.get("id").asText()).isEqualTo("reference");
    assertThat(operation.get("enabled").asBoolean()).isTrue();
  }

  private Model getHomerModel() {
    return JenaUtils.read(new ClassPathResource("operation/homer.ttl"));
  }

}
