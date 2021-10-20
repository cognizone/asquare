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

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// TODO negate option is not tested yet!

@SpringBootTest(classes = {OperationResultProcessorTestConfig.class})
public class OperationResultProcessorTest {

  @Autowired
  OperationResultJsonConversion personConversion;

  @Autowired
  OperationResultProcessor personOperations;

  @Autowired
  OperationResultJsonConversion rootConversion;

  @Autowired
  OperationResultProcessor rootOperations;

  private static final String homer = "http://demo.com/data#homer";

  @Test
  public void root_group() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    assertThat(result.getContextUri()).isEqualTo(homer);
    assertThat(result.getOperationGroup().getId()).isEqualTo("person-data");
    assertThat(result.getOperationGroup().getPathId()).isEqualTo("person-data");
  }

  @Test
  public void nested_group_no_selector() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    OperationResultProcessor.SingleGroupResult groupResult = result.getGroupResult("simple-operations");
    assertThat(groupResult).isNotNull();
    assertThat(groupResult.getContextUri()).isEqualTo(homer);
    assertThat(groupResult.getOperationGroup().getId()).isEqualTo("simple-operations");
    assertThat(groupResult.getOperationGroup().getPathId()).isEqualTo("person-data/simple-operations");

    assertThat(groupResult.getOperationGroup().hasOperations()).isTrue();
    assertThat(groupResult.getOperationGroup().getOperations().size()).isEqualTo(4);
    assertThat(groupResult.getOperationResults().size()).isEqualTo(4);
  }

  @Test
  public void operation_true() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    OperationResultProcessor.SingleGroupResult groupResult = result.getGroupResult("simple-operations");
    OperationResult operationResult = groupResult.getOperationResult("is-true");
    assertThat(operationResult).isNotNull();
    assertThat(operationResult.getOperation().getId()).isEqualTo("is-true");
    assertThat(operationResult.isEnabled()).isTrue();
  }

  @Test
  public void operation_false() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    OperationResultProcessor.SingleGroupResult groupResult = result.getGroupResult("simple-operations");
    OperationResult operationResult = groupResult.getOperationResult("is-false");
    assertThat(operationResult).isNotNull();
    assertThat(operationResult.getOperation().getId()).isEqualTo("is-false");
    assertThat(operationResult.isEnabled()).isFalse();
  }

  @Test
  public void operation_query_true() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    OperationResultProcessor.SingleGroupResult groupResult = result.getGroupResult("simple-operations");
    OperationResult operationResult = groupResult.getOperationResult("has-name");
    assertThat(operationResult.isEnabled()).isTrue();
  }

  @Test
  public void operation_query_false() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    OperationResultProcessor.SingleGroupResult groupResult = result.getGroupResult("simple-operations");
    OperationResult operationResult = groupResult.getOperationResult("has-brain");
    assertThat(operationResult.isEnabled()).isFalse();
  }

  @Test
  public void nested_group_with_selector() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    OperationResultProcessor.SingleGroupResult groupResult = result.getGroupResult("requires-group");
    assertThat(groupResult).isNotNull();
    assertThat(groupResult.getContextUri()).isEqualTo("http://demo.com/data#marge");
  }

  @Test
  public void same_level_requires_true() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    OperationResultProcessor.SingleGroupResult groupResult = result.getGroupResult("requires-group");
    OperationResult operationResult = groupResult.getOperationResult("same-level-requires-true");
    assertThat(operationResult.isEnabled()).isTrue();
  }

  @Test
  public void same_level_requires_false() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    OperationResultProcessor.SingleGroupResult groupResult = result.getGroupResult("requires-group");
    OperationResult operationResult = groupResult.getOperationResult("same-level-requires-false");
    assertThat(operationResult.isEnabled()).isFalse();
  }

  @Test
  public void same_level_requires_multiple() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    OperationResultProcessor.SingleGroupResult groupResult = result.getGroupResult("requires-group");
    OperationResult operationResult = groupResult.getOperationResult("same-level-requires-multiple");
    assertThat(operationResult.isEnabled()).isFalse();
  }

  @Test
  public void nested_requires() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    OperationResultProcessor.SingleGroupResult groupResult = result.getGroupResult("requires-group");
    OperationResult operationResult = groupResult.getOperationResult("nested-requires");
    assertThat(operationResult.isEnabled()).isTrue();
  }

  @Test
  public void up_requires() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    OperationResultProcessor.SingleGroupResult groupResult = result.getGroupResult("requires-group");
    OperationResult operationResult = groupResult.getOperationResult("up-requires");
    assertThat(operationResult.isEnabled()).isTrue();
  }

  @Test
  public void up_branch_requires() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    OperationResultProcessor.SingleGroupResult groupResult = result.getGroupResult("requires-group");
    OperationResult operationResult = groupResult.getOperationResult("up-branch-requires");
    assertThat(operationResult.isEnabled()).isTrue();
  }

  @Test
  public void multiple_requires() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    OperationResultProcessor.SingleGroupResult groupResult = result.getGroupResult("requires-group");
    OperationResult operationResult = groupResult.getOperationResult("multiple-requires");
    assertThat(operationResult.isEnabled()).isTrue();
  }

  @Test
  public void same_level_optional_true() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    OperationResultProcessor.SingleGroupResult groupResult = result.getGroupResult("optional-group");
    OperationResult operationResult = groupResult.getOperationResult("same-level-optional-true");
    assertThat(operationResult.isEnabled()).isTrue();
  }

  @Test
  public void same_level_optional_false() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    OperationResultProcessor.SingleGroupResult groupResult = result.getGroupResult("optional-group");
    OperationResult operationResult = groupResult.getOperationResult("same-level-optional-false");
    assertThat(operationResult.isEnabled()).isFalse();
  }

  @Test
  public void same_level_optional_multiple() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    OperationResultProcessor.SingleGroupResult groupResult = result.getGroupResult("optional-group");
    OperationResult operationResult = groupResult.getOperationResult("same-level-optional-multiple");
    assertThat(operationResult.isEnabled()).isTrue();
  }

  @Test
  public void nested_optional() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    OperationResultProcessor.SingleGroupResult groupResult = result.getGroupResult("optional-group");
    OperationResult operationResult = groupResult.getOperationResult("nested-optional");
    assertThat(operationResult.isEnabled()).isTrue();
  }

  @Test
  public void up_optional() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    OperationResultProcessor.SingleGroupResult groupResult = result.getGroupResult("optional-group");
    OperationResult operationResult = groupResult.getOperationResult("up-optional");
    assertThat(operationResult.isEnabled()).isTrue();
  }

  @Test
  public void up_branch_optional() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    OperationResultProcessor.SingleGroupResult groupResult = result.getGroupResult("optional-group");
    OperationResult operationResult = groupResult.getOperationResult("up-branch-optional");
    assertThat(operationResult.isEnabled()).isTrue();
  }

  @Test
  public void multiple_optional() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    OperationResultProcessor.SingleGroupResult groupResult = result.getGroupResult("optional-group");
    OperationResult operationResult = groupResult.getOperationResult("multiple-optional");
    assertThat(operationResult.isEnabled()).isTrue();
  }

  @Test
  public void empty_group() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    OperationResultProcessor.SingleGroupResult groupResult = result.getGroupResult("empty-group");
    assertThat(groupResult).isNotNull();
    assertThat(groupResult.getContextUri()).isNull();
  }

  @Test
  public void nested_empty_group() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    OperationResultProcessor.SingleGroupResult groupResult = result.getGroupResult("empty-group");
    OperationResultProcessor.SingleGroupResult nestedGroupResult = groupResult.getGroupResult("nested-empty-group");
    assertThat(nestedGroupResult).isNotNull();
    assertThat(nestedGroupResult.getContextUri()).isNull();
  }

  @Test
  public void empty_operation() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    OperationResultProcessor.SingleGroupResult groupResult = result.getGroupResult("empty-group");
    OperationResult operationResult = groupResult.getOperationResult("empty-is-true");
    assertThat(operationResult).isNotNull();
    assertThat(operationResult.isEnabled()).isFalse();
  }

  @Test
  public void children_group() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    List<OperationResultProcessor.SingleGroupResult> children = result.getGroupResults("children");
    assertThat(children).isNotNull();
    assertThat(children.size()).isEqualTo(3);
  }

  @Test
  public void children_operations() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    List<OperationResultProcessor.SingleGroupResult> children = result.getGroupResults("children");

    OperationResultProcessor.SingleGroupResult bart = find(children, "http://demo.com/data#bart");
    assertThat(bart.getOperationResults().size()).isEqualTo(3);

    OperationResultProcessor.SingleGroupResult lisa = find(children, "http://demo.com/data#lisa");
    assertThat(lisa.getOperationResults().size()).isEqualTo(3);

    OperationResultProcessor.SingleGroupResult maggie = find(children, "http://demo.com/data#maggie");
    assertThat(maggie.getOperationResults().size()).isEqualTo(3);
  }

  @Test
  public void children_talking() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    List<OperationResultProcessor.SingleGroupResult> children = result.getGroupResults("children");

    OperationResultProcessor.SingleGroupResult bart = find(children, "http://demo.com/data#bart");
    assertThat(bart.getOperationResult("talks").isEnabled()).isTrue();

    OperationResultProcessor.SingleGroupResult lisa = find(children, "http://demo.com/data#lisa");
    assertThat(lisa.getOperationResult("talks").isEnabled()).isTrue();

    OperationResultProcessor.SingleGroupResult maggie = find(children, "http://demo.com/data#maggie");
    assertThat(maggie.getOperationResult("talks").isEnabled()).isFalse();
  }

  @Test
  public void children_nested_group() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    List<OperationResultProcessor.SingleGroupResult> children = result.getGroupResults("children");
    OperationResultProcessor.SingleGroupResult bart = find(children, "http://demo.com/data#bart");
    OperationResultProcessor.SingleGroupResult groupResult = bart.getGroupResult("children-nested");
    assertThat(groupResult).isNotNull();
  }


  @Test
  public void children_siblings_group() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    List<OperationResultProcessor.SingleGroupResult> children = result.getGroupResults("children");
    OperationResultProcessor.SingleGroupResult bart = find(children, "http://demo.com/data#bart");
    List<OperationResultProcessor.SingleGroupResult> groupResults = bart.getGroupResults("children-siblings");
    assertThat(groupResults.size()).isEqualTo(2);
  }

  @Test
  public void all_children_talking_using_star_operator() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    OperationResult operationResult = result.getOperationResult("talking-heads");
    assertThat(operationResult.isEnabled()).isFalse();
  }

  @Test
  public void all_children_named_using_star_operator() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    OperationResult operationResult = result.getOperationResult("named-children");
    assertThat(operationResult.isEnabled()).isTrue();
  }


  @Test
  public void all_children_married_using_star_operator() {
    // given / when
    OperationResultProcessor.SingleGroupResult result = initializeHomer();

    // then
    OperationResult operationResult = result.getOperationResult("married-children");
    assertThat(operationResult.isEnabled()).isFalse();
  }

  @Test
  public void output_json() {
    // given
    Model model = getHomerModel();

    // when
    Set<String> allPermissions = personOperations.getOperationRoot().getOperationIds();
    ObjectNode standaloneJson = personConversion.createStandaloneJson(allPermissions,
                                                                      model,
                                                                      homer,
                                                                      "person-data");

    // then
    System.out.println(standaloneJson); // pretty string ?
  }

  @Test
  public void root_operations() {
    // given
    Model model = getHomerModel();

    // when
    Set<String> allPermissions = rootOperations.getOperationRoot().getOperationIds();
    ObjectNode standaloneJson = rootConversion.createStandaloneJson(allPermissions,
                                                                    model,
                                                                    homer,
                                                                    "three");

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

  private OperationResultProcessor.SingleGroupResult find(List<OperationResultProcessor.SingleGroupResult> children,
                                                          String uri) {
    return children.stream()
                   .filter(child -> child.getContextUri().equals(uri))
                   .findFirst()
                   .get();
  }


  private OperationResultProcessor.SingleGroupResult initializeHomer() {
    // given
    Model model = getHomerModel();

    // when
    Set<String> allPermissions = personOperations.getOperationRoot().getOperationIds();
    OperationResultProcessor.SingleGroupResult result = personOperations.validate(allPermissions,
                                                                                  model,
                                                                                  homer,
                                                                                  "person-data");

    System.out.println("result = \n" + result);
    return result;
  }
}
