package zone.cogni.asquare.cube.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.cube.operation.OperationResultProcessor.SingleGroupResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class OperationResultJsonConversion {

  private static final Logger log = LoggerFactory.getLogger(OperationResultJsonConversion.class);

  private final OperationConfiguration configuration;
  private final OperationResultProcessor operationResultProcessor;

  public OperationResultJsonConversion(OperationConfiguration configuration, OperationResultProcessor operationResultProcessor) {
    this.configuration = configuration;
    this.operationResultProcessor = operationResultProcessor;
  }

  /**
   * @param modelSupplier supplier of a single model containing all graphs of all uris
   * @return JSON array with operations, one for each uri
   */
  public JsonNode createStandaloneJson(Supplier<Set<String>> permissionsSupplier,
                                       Supplier<Model> modelSupplier,
                                       List<String> uris,
                                       String operationGroupId) {
    ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();

    List<SingleGroupResult> results = operationResultProcessor.validate(permissionsSupplier, modelSupplier, uris, operationGroupId);
    results.forEach(result -> arrayNode.add(createGroupJson(result)));

    return arrayNode;
  }

  public ObjectNode createStandaloneJson(Supplier<Set<String>> permissionsSupplier,
                                         String operationGroupId) {
    Model model = ModelFactory.createDefaultModel();
    String uri = StringUtils.EMPTY;

    return createStandaloneJson(permissionsSupplier, () -> model, uri, operationGroupId);
  }

  public ObjectNode createStandaloneJson(Supplier<Set<String>> permissions,
                                         Supplier<Model> model,
                                         String uri,
                                         String operationGroupId) {
    return createStandaloneJson(permissions.get(), model.get(), uri, operationGroupId);
  }

  /**
   * Use another createStandaloneJson method.
   */
  @Deprecated
  public ObjectNode createStandaloneJson(Supplier<Set<String>> permissions, Model model, String uri, String operationGroupId) {
    return createStandaloneJson(permissions.get(), model, uri, operationGroupId);
  }

  public ObjectNode createStandaloneJson(Set<String> permissions, Model model, String uri, String operationGroupId) {
    SingleGroupResult result = operationResultProcessor.validate(permissions, model, uri, operationGroupId);
    return createGroupJson(result);
  }

  private ObjectNode createGroupJson(SingleGroupResult groupResult) {
    ObjectNode groupJson = JsonNodeFactory.instance.objectNode();

    OperationGroup operationGroup = groupResult.getOperationGroup();
    groupJson.put("id", operationGroup.getId());
    processDescription(groupJson, operationGroup.getDescription());
    processSelectorQuery(groupJson, operationGroup);
    groupJson.put("uri", groupResult.getContextUri());

    if (groupResult.hasOperationResults()) {
      ArrayNode operationArray = JsonNodeFactory.instance.arrayNode();
      groupJson.set("operations", operationArray);

      List<OperationResult> operationResults = new ArrayList<>(groupResult.getOperationResults());
//      operationResults.sort(Comparator.comparing(o -> o.getOperation().getId()));
      operationResults.forEach(operationResult -> operationArray.add(createOperationJson(operationResult)));
    }

    if (groupResult.hasGroupResults()) {
      ArrayNode groupArray = JsonNodeFactory.instance.arrayNode();
      groupJson.set("groups", groupArray);

      List<SingleGroupResult> groupResults = new ArrayList<>(groupResult.getGroupResults());
//      groupResults.sort(Comparator.comparing(o -> o.getOperationGroup().getId()));
      groupResults.forEach(childGroupResult -> groupArray.add(createGroupJson(childGroupResult)));
    }

    return groupJson;
  }


  private ObjectNode createOperationJson(OperationResult operationResult) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();

    Operation operation = operationResult.getOperation();
    result.put("id", operation.getId());
    processDescription(result, operation.getDescription());
    result.put("enabled", operationResult.isEnabled());

    processOperation(result, operation);

    return result;
  }

  private void processDescription(ObjectNode groupJson, String description) {
    if (!configuration.mustOutput("description")) return;
    if (!StringUtils.isNotBlank(description)) return;

    groupJson.put("description", description);
  }

  private void processSelectorQuery(ObjectNode groupJson, OperationGroup operationGroup) {
    if (!configuration.mustOutput("selectorQuery")) return;
    if (!operationGroup.hasSelectorQuery()) return;

    groupJson.put("selectorQuery", operationGroup.getCompactSelectorQuery());
  }

  private void processOperation(ObjectNode operationResultNode, Operation operation) {
    if (!configuration.mustOutput("operation")) return;

    if (!configuration.mustOutput("requires") && !configuration.mustOutput("template")) return;

    ObjectNode operationNode = JsonNodeFactory.instance.objectNode();
    operationResultNode.set("operation", operationNode);

    processTemplate(operationNode, operation);
    processRequires(operationNode, operation);
  }

  private void processTemplate(ObjectNode operationNode, Operation operation) {
    if (!configuration.mustOutput("template")) return;
    if (!operation.hasTemplate()) return;

    operationNode.put("template", operation.getCompactFullTemplate());
  }

  private void processRequires(ObjectNode operationNode, Operation operation) {
    if (!configuration.mustOutput("requires")) return;
    if (operation.getRequires() == null || operation.getRequires().isEmpty()) return;

    ArrayNode requiresArray = JsonNodeFactory.instance.arrayNode();
    operationNode.set("requires", requiresArray);
    operation.getRequires().forEach(require -> {
      requiresArray.add(String.join("/", require));
    });
  }
}
