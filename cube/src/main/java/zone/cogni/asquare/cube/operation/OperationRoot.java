package zone.cogni.asquare.cube.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamSource;
import zone.cogni.asquare.cube.json5.Json5Light;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OperationRoot {

  private static final Logger log = LoggerFactory.getLogger(OperationRoot.class);

  public static OperationRoot load(InputStreamSource resource) {
    try {
      log.info("load json {}", resource);

      ObjectMapper objectMapper = Json5Light.getJson5Mapper();
      OperationRoot result = objectMapper.readValue(resource.getInputStream(), OperationRoot.class);

      log.info("load json done");
      return result;
    }
    catch (IOException e) {
      throw new RuntimeException("Unable to load operation configuration.", e);
    }
  }

  private Map<String, String> prefixes;
  private List<OperationGroup> operationGroups;

  private final Map<String, OperationGroup> operationGroupMap = new TreeMap<>();
  private final Map<String, Operation> operationMap = new TreeMap<>();

  public Map<String, String> getPrefixes() {
    return prefixes;
  }

  public void setPrefixes(Map<String, String> prefixes) {
    this.prefixes = prefixes;
  }

  public List<OperationGroup> getOperationGroups() {
    return operationGroups;
  }

  public void setOperationGroups(List<OperationGroup> operationGroups) {
    this.operationGroups = operationGroups;
  }

  public void validate() {
    makeParentStructure();

    List<String> errorMessages = new ArrayList<>();

    validateIds(errorMessages);
    validateOptional(errorMessages);
    validateRequires(errorMessages);
    validateOperations(errorMessages);


    if (!errorMessages.isEmpty()) {
      String separator = "\n  - ";
      String errorMessage = StringUtils.join(errorMessages, separator);
      throw new RuntimeException("Problems with operations JSON found: " + separator + errorMessage);
    }

    // if all validates we can set requires!
    makeOptionalStructure();
    makeRequiresStructure();
  }

  private void makeOptionalStructure() {
    operationGroups.forEach(this::makeOptionalStructure);
  }

  private void makeOptionalStructure(OperationGroup operationGroup) {
    if (operationGroup.hasOperations()) {
      operationGroup.getOperations()
                    .forEach(this::makeOptionalStructure);
    }

    if (operationGroup.hasOperationGroups()) {
      operationGroup.getOperationGroups()
                    .forEach(this::makeOptionalStructure);
    }
  }

  private void makeOptionalStructure(Operation operation) {
    if (!operation.hasOptional()) return;

    operation.setOptionalOperations(calculateOptionalOperations(operation));
  }

  @Nonnull
  private List<Operation> calculateOptionalOperations(Operation operation) {
    return operation.getOptional()
                    .stream()
                    .map(optionalPath -> findOperation(operation, optionalPath))
                    .collect(Collectors.toList());
  }


  private void makeRequiresStructure() {
    operationGroups.forEach(this::makeRequiresStructure);
  }

  private void makeRequiresStructure(OperationGroup operationGroup) {
    if (operationGroup.hasOperations()) {
      operationGroup.getOperations()
                    .forEach(this::makeRequiresStructure);
    }
    if (operationGroup.hasOperationGroups()) {
      operationGroup.getOperationGroups()
                    .forEach(this::makeRequiresStructure);
    }
  }

  private void makeRequiresStructure(Operation operation) {
    if (!operation.hasRequires()) return;

    operation.setRequiresOperations(calculateRequiresOperations(operation));
  }

  @Nonnull
  private List<Operation> calculateRequiresOperations(Operation operation) {
    return operation.getRequires()
                    .stream()
                    .map(requiresPath -> findOperation(operation, requiresPath))
                    .collect(Collectors.toList());
  }

  private Operation findOperation(Operation operation, List<String> path) {
    try {

      OperationGroup current = operation.getParent();
      for (int i = 0; i < path.size() - 1; i++) {
        String id = path.get(i);

        if (id.equals("..")) current = current.getParent();
        if (id.endsWith("*")) current.getOperationGroup(id.substring(0, id.length() - 1));
        else current.getOperationGroup(id);
      }

      return current.getOperation(path.get(path.size() - 1));
    }
    catch (RuntimeException e) {
      throw e;
    }
  }

  private void makeParentStructure() {
    operationGroups.forEach(this::makeParentStructure);
  }

  private void makeParentStructure(OperationGroup operationGroup) {
    if (operationGroup.hasOperations()) {
      operationGroup.getOperations()
                    .forEach(operation -> {
                      operation.setParent(operationGroup);
                      operation.getPathId(); // make sure it is initialized
                    });
    }

    if (operationGroup.hasOperationGroups()) {
      operationGroup.getOperationGroups()
                    .forEach(childGroup -> {
                      childGroup.setParent(operationGroup);
                      childGroup.getPathId(); // make sure it is initialized
                      makeParentStructure(childGroup);
                    });
    }
  }

  private void validateIds(List<String> errorMessages) {
    validateIdsForGroups(errorMessages, null, operationGroups);
  }

  private void validateIdsForGroups(List<String> errorMessages,
                                    OperationGroup parent,
                                    List<OperationGroup> operationGroups) {


    Stream<String> idStream = operationGroups.stream().map(OperationGroup::getId);
    Set<String> duplicates = findDuplicates(idStream);

    if (!duplicates.isEmpty()) {
      errorMessages.add("Duplicates groups found in path " + getPath(parent) +
                        " and ids " + duplicates);
    }

    operationGroups.forEach(operationGroup -> {
      validateIdsForGroup(errorMessages, parent, operationGroup);
    });
  }

  @Nonnull
  private String getPath(OperationGroup operationGroup) {
    return operationGroup == null ? "" : String.join("/", operationGroup.getPath());
  }

  private void validateIdsForGroup(List<String> errorMessages,
                                   OperationGroup parent,
                                   OperationGroup operationGroup) {
    if (operationGroup.hasOperationGroups()) {
      validateIdsForGroups(errorMessages, parent, operationGroup.getOperationGroups());
    }

    if (operationGroup.hasOperations()) {
      validateIdsForOperations(errorMessages, parent, operationGroup);
    }
  }

  private void validateIdsForOperations(List<String> errorMessages,
                                        OperationGroup parent,
                                        OperationGroup operationGroup) {
    Stream<String> idStream = operationGroup.getOperations().stream().map(Operation::getId);
    Set<String> duplicates = findDuplicates(idStream);

    if (!duplicates.isEmpty())
      errorMessages.add("Duplicates operations found in path " + String.join("/", getPath(parent))
                        + " and ids " + duplicates);
  }

  private <T> Set<T> findDuplicates(Stream<T> stream) {
    Set<T> uniques = new HashSet<>();
    return stream.filter(e -> !uniques.add(e))
                 .collect(Collectors.toSet());
  }

  private void validateOptional(List<String> errorMessages) {
    operationGroups.forEach(operationGroup -> validateOptional(errorMessages, operationGroup));
  }

  private void validateOptional(List<String> errorMessages, OperationGroup operationGroup) {
    if (operationGroup.hasOperationGroups()) {
      operationGroup.getOperationGroups().forEach(childGroup -> {
        validateOptional(errorMessages, childGroup);
      });
    }

    if (operationGroup.hasOperations()) {
      operationGroup.getOperations().forEach(operation -> {
        validateOptional(errorMessages, operation);
      });
    }
  }

  private void validateOptional(List<String> errorMessages, Operation operation) {
    if (!operation.hasOptional()) return;

    operation.getOptional().forEach(optionalPath -> {
      validateOptionalPath(errorMessages, operation, optionalPath);
    });
  }

  private void validateOptionalPath(List<String> errorMessages,
                                    Operation operation,
                                    List<String> path) {
    String errorMessageIntro = "invalid optional at " + getPath(operation.getParent()) +
                               " and operation " + operation.getId() +
                               " and requires " + String.join("/", path) + ": ";

    validatePath(errorMessages, operation, path, errorMessageIntro);
  }

  private void validatePath(List<String> errorMessages, Operation operation, List<String> path, String errorMessageIntro) {
    OperationGroup currentGroup = operation.getParent();

    for (int i = 0; i < path.size() - 1; i++) {
      String operationGroupId = path.get(i);

      // skip * at end
      operationGroupId = operationGroupId.endsWith("*") ? operationGroupId.substring(0, operationGroupId.length() - 1)
                                                        : operationGroupId;
      currentGroup = currentGroup.getOperationGroup(operationGroupId);
      if (currentGroup == null) {
        errorMessages.add(errorMessageIntro + "cannot find group " + operationGroupId);
        return;
      }
    }

    String operationId = path.get(path.size() - 1);
    Operation referencedOperation = currentGroup.getOperation(operationId);
    if (referencedOperation == null) {
      errorMessages.add(errorMessageIntro + "cannot find operation " + operationId);
    }
  }

  private void validateRequires(List<String> errorMessages) {
    operationGroups.forEach(operationGroup -> validateRequires(errorMessages, operationGroup));
  }

  private void validateRequires(List<String> errorMessages, OperationGroup operationGroup) {
    if (operationGroup.hasOperationGroups()) {
      operationGroup.getOperationGroups().forEach(childGroup -> {
        validateRequires(errorMessages, childGroup);
      });
    }

    if (operationGroup.hasOperations()) {
      operationGroup.getOperations().forEach(operation -> {
        validateRequires(errorMessages, operation);
      });
    }
  }

  private void validateRequires(List<String> errorMessages,
                                Operation operation) {
    if (!operation.hasRequires()) return;

    operation.getRequires().forEach(requiresPath -> {
      validateRequiresPath(errorMessages, operation, requiresPath);
    });
  }

  private void validateRequiresPath(List<String> errorMessages,
                                    Operation operation,
                                    List<String> path) {
    String errorMessageIntro = "invalid requires at " + getPath(operation.getParent()) +
                               " and operation " + operation.getId() +
                               " and requires " + String.join("/", path) + ": ";

    validatePath(errorMessages, operation, path, errorMessageIntro);
  }

  private void validateOperations(List<String> errorMessages) {
    operationGroups.forEach(operationGroup -> validateOperations(errorMessages, operationGroup));
  }

  private void validateOperations(List<String> errorMessages, OperationGroup operationGroup) {
    validateOperationGroup(errorMessages, operationGroup);

    if (operationGroup.hasOperations()) {
      operationGroup.getOperations()
                    .forEach(operation -> validateOperation(errorMessages, operation));
    }

    if (operationGroup.hasOperationGroups()) {
      operationGroup.getOperationGroups()
                    .forEach(childGroup -> validateOperations(errorMessages, childGroup));
    }
  }

  private void validateOperationGroup(List<String> errorMessages, OperationGroup operationGroup) {
    operationGroupMap.put(operationGroup.getPathId(), operationGroup);

    if (operationGroup.getId() == null) {
      errorMessages.add("operation group without id on path " + operationGroup.getPath());
    }

    if (operationGroup.hasSelectorQuery()) {
      try {
        operationGroup.getContextSelectorQuery(getPrefixQuery());
      }
      catch (RuntimeException e) {
        String sparql = operationGroup.getContextSelectorSparql(getPrefixQuery());
        errorMessages.add("Invalid selector for group path " + operationGroup.getPathId() + ": \n" + sparql);
      }
    }
  }

  private void validateOperation(List<String> errorMessages, Operation operation) {
    operationMap.put(operation.getPathId(), operation);

    if (operation.getId() == null) {
      errorMessages.add("operation without id !!");
    }

    int propertyCount = 0;
    if (operation.hasTemplate()) propertyCount += 1;
    if (operation.hasRequires()) propertyCount += 1;
    if (operation.hasOptional()) propertyCount += 1;

    String pathId = operation.getPathId();
    if (propertyCount == 0)
      errorMessages.add("operation with path " + pathId + " is missing template, requires or optional attribute.");
    else if (propertyCount > 1)
      errorMessages.add("operation with path " + pathId + " has both template and requires attributes.");

    validateTemplate(errorMessages, operation);
  }

  private void validateTemplate(List<String> errorMessages, Operation operation) {
    if (!operation.hasTemplate()) return;

    Operation.TemplateType templateType = operation.getTemplateType();
    if (templateType == Operation.TemplateType.not_available) {
      errorMessages.add("Invalid operation template: \n" + operation.getFullTemplate());
    }
    else if (templateType == Operation.TemplateType.ask_query) {
      try {
        operation.getContextQuery(getPrefixQuery());
      }
      catch (RuntimeException e) {
        errorMessages.add("Invalid query for operation: \n" + operation.getContextSparql(getPrefixQuery()));
      }
    }

  }

  public String getPrefixQuery() {
    return prefixes.entrySet()
                   .stream()
                   .map(e -> "PREFIX "
                             + StringUtils.rightPad(e.getKey() + ":", 8) + " <" + e.getValue() + ">\n")
                   .collect(Collectors.joining())
           + "\n";
  }

  public OperationGroup getByPathId(String pathId) {
    OperationGroup result = operationGroupMap.get(pathId);
    if (result == null) throw new RuntimeException("cannot find group with path '" + pathId + "'");

    return result;
  }

  // TODO cleanup?
  public List<OperationGroup> getSortedOperationGroups(String operationGroupId) {
    throw new UnsupportedOperationException();
  }

  // TODO cleanup?
  public List<Operation> getSortedOperations(String operationGroupId) {
    throw new UnsupportedOperationException();
  }

  // TODO make immutable?
  public Set<String> getOperationIds() {
    return operationMap.keySet();
  }

  public Set<Operation> getOperationsForPathIds(Set<String> pathIds) {
    return pathIds.stream().map(this::mapToOperation).collect(Collectors.toSet());
  }

  private Operation mapToOperation(String pathId) {
    return operationMap.get(pathId);
  }
}
