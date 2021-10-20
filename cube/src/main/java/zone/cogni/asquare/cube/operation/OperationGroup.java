package zone.cogni.asquare.cube.operation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class OperationGroup {

  private String id;
  private String description;
  private String selectorQuery;
  private boolean multiselect;
  private List<Operation> operations;
  private List<OperationGroup> operationGroups;

  @JsonIgnore
  private Query contextSelectorQuery;

  @JsonIgnore
  private OperationGroup parent;

  @JsonIgnore
  private List<String> path;

  @JsonIgnore
  private String pathId;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getSelectorQuery() {
    return selectorQuery;
  }

  public void setSelectorQuery(String selectorQuery) {
    this.selectorQuery = selectorQuery;
  }

  public boolean isMultiselect() {
    return multiselect;
  }

  public void setMultiselect(boolean multiselect) {
    this.multiselect = multiselect;
  }

  public void setOperations(List<Operation> operations) {
    this.operations = operations;
  }

  public List<OperationGroup> getOperationGroups() {
    return operationGroups;
  }

  public void setOperationGroups(List<OperationGroup> operationGroups) {
    this.operationGroups = operationGroups;
  }

  public Query getContextSelectorQuery() {
    return contextSelectorQuery;
  }

  public OperationGroup getParent() {
    return parent;
  }

  public void setParent(OperationGroup parent) {
    this.parent = parent;
  }

  public List<String> getPath() {
    if (path == null) path = calculatePath();
    return path;
  }

  @Nonnull
  private List<String> calculatePath() {
    List<String> result = new ArrayList<>();

    if (parent != null) {
      result.addAll(parent.getPath());
      result.add(parent.getId());
    }

    return result;
  }

  @Nonnull
  public String getPathId() {
    if (pathId == null) pathId = calculatePathId();
    return pathId;
  }

  @Nonnull
  private String calculatePathId() {
    List<String> path = getPath();
    if (path.isEmpty()) return id;

    return String.join("/", path) + "/" + id;
  }

  @JsonIgnore
  public String getCompactSelectorQuery() {
    if (!hasSelectorQuery()) return "";

    String result = selectorQuery.replace('\n', ' ');
    int oldSize = result.length();
    while (true) {
      result = result.replace("  ", " ");
      if (result.length() == oldSize) break;

      oldSize = result.length();
    }

    return result;
  }

  public boolean hasSelectorQuery() {
    return StringUtils.isNotBlank(selectorQuery);
  }

  @Nonnull
  public Query getContextSelectorQuery(String prefixPart) {
    if (contextSelectorQuery == null) {
      contextSelectorQuery = QueryFactory.create(getContextSelectorSparql(prefixPart));
    }

    return contextSelectorQuery;
  }

  @Nonnull
  public String getContextSelectorSparql(String prefixPart) {
    String spelSparql = prefixPart + selectorQuery;
    return StringUtils.replace(spelSparql, "<#{[uri]}>", "?contextUri");
  }

  public boolean hasOperationGroups() {
    return operationGroups != null && !operationGroups.isEmpty();
  }

  public boolean hasOperations() {
    return operations != null && !operations.isEmpty();
  }

  public List<Operation> getOperations() {
    return operations == null ? Collections.emptyList() : operations;
  }

  /**
   * @return null if group with id is not found
   */
  public OperationGroup getOperationGroup(String id) {
    if (id.equals("..")) return getParent();

    if (operationGroups == null) return null;

    return operationGroups.stream()
                          .filter(operationGroup -> operationGroup.getId().equals(id))
                          .findFirst()
                          .orElse(null);
  }

  /**
   * @return null if id does not match any operation
   */
  public Operation getOperation(String id) {
    if (operations == null) return null;

    return operations.stream()
                     .filter(operation -> operation.getId().equals(id))
                     .findFirst()
                     .orElse(null);
  }

  @Override
  public String toString() {
    return "OperationGroup{" +
           "pathId=" + pathId +
           (selectorQuery == null ? "" : ", selectorQuery='" + selectorQuery + "'") +
           "}";
  }
}
