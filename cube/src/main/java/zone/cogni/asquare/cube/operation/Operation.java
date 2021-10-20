package zone.cogni.asquare.cube.operation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


@JsonInclude(JsonInclude.Include.NON_NULL)
public class Operation {

  private static final Logger log = LoggerFactory.getLogger(Operation.class);

  public enum TemplateType {
    not_available,
    boolean_value,
    ask_query
  }

  private String id;
  private String label;
  private String description;

  private boolean negate;
  private List<List<String>> requires;
  private List<List<String>> optional;
  private String template;

  @JsonIgnore
  private TemplateType templateType;

  @JsonIgnore
  private Query contextQuery;

  @JsonIgnore
  private boolean isContextQuery = true;

  @JsonIgnore
  private OperationGroup parent;

  @JsonIgnore
  private List<String> path;

  @JsonIgnore
  private String pathId;

  @JsonIgnore
  private List<Operation> requiresOperations;

  @JsonIgnore
  private List<Operation> optionalOperations;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public boolean isNegate() {
    return negate;
  }

  public void setNegate(boolean negate) {
    this.negate = negate;
  }

  public List<List<String>> getRequires() {
    return requires;
  }

  public void setRequires(List<List<String>> requires) {
    this.requires = requires;
  }

  public List<List<String>> getOptional() {
    return optional;
  }

  public void setOptional(List<List<String>> optional) {
    this.optional = optional;
  }

  public String getTemplate() {
    return template;
  }

  public void setTemplate(String template) {
    this.template = template;
  }

  public Query getContextQuery() {
    return contextQuery;
  }

  public OperationGroup getParent() {
    return parent;
  }

  public void setParent(OperationGroup parent) {
    this.parent = parent;
  }

  public List<String> getPath() {
    if (path == null) this.path = calculatePath();
    return path;
  }

  public List<Operation> getRequiresOperations() {
    if (!hasRequiresOperations()) {
      throw new RuntimeException("illegal access: code should not try to access 'requiresOperations' when there are none.");
    }

    return requiresOperations;
  }

  private boolean hasRequiresOperations() {
    return requiresOperations != null && !requiresOperations.isEmpty();
  }

  public void setRequiresOperations(List<Operation> requiresOperations) {
    this.requiresOperations = requiresOperations;
  }

  public List<Operation> getOptionalOperations() {
    if (!hasOptionalOperations()) {
      throw new RuntimeException("illegal access: code should not try to access 'optionalOperations' when there are none.");
    }

    return optionalOperations;
  }

  private boolean hasOptionalOperations() {
    return optionalOperations != null && !optionalOperations.isEmpty();
  }

  public void setOptionalOperations(List<Operation> optionalOperations) {
    this.optionalOperations = optionalOperations;
  }

  @Nonnull
  @SuppressWarnings("CollectionAddAllCanBeReplacedWithConstructor")
  private List<String> calculatePath() {
    List<String> result = new ArrayList<>();
    result.addAll(parent.getPath());
    result.add(parent.getId());
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

  @Nullable
  public TemplateType getTemplateType() {
    String template = getFullTemplate();

    if (StringUtils.isBlank(template)) return TemplateType.not_available;

    if (template.equals("true") || template.equals("false")) return TemplateType.boolean_value;

    return TemplateType.ask_query;
  }

  public boolean hasTemplate() {
    return StringUtils.isNotBlank(getFullTemplate());
  }

  public boolean hasRequires() {
    return requires != null && !requires.isEmpty();
  }

  public boolean hasOptional() {
    return optional != null && !optional.isEmpty();
  }

  @JsonIgnore
  public String getCompactFullTemplate() {
    String result = getFullTemplate().replace('\n', ' ');
    int oldSize = result.length();
    while (true) {
      result = result.replace("  ", " ");
      if (result.length() == oldSize) break;

      oldSize = result.length();
    }

    return result;
  }

  @JsonIgnore
  public String getFullTemplate() {
    if (template == null || template.isEmpty()) return "";

    return template.trim();
  }

  public Query getContextQuery(String prefixPart) {
    try {
      contextQuery = isContextQuery ? QueryFactory.create(getContextSparql(prefixPart)) : null;
    }
    catch (Exception e) {
      isContextQuery = false;
      log.warn("Cannot create context query from template:\n" +
               "{}", getFullTemplate(), e);
    }

    return contextQuery;
  }

  @Nonnull
  public String getContextSparql(String prefixPart) {
    String spelSparql = prefixPart + getFullTemplate();
    return StringUtils.replace(spelSparql, "<#{[uri]}>", "?contextUri");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Operation operation = (Operation) o;
    return pathId.equals(operation.pathId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(pathId);
  }

  @Override
  public String toString() {
    return "Operation{" +
           "pathId=" + pathId +
           ", requires=" + requires +
           ", optional=" + optional +
           ", template=" + template +
           ", templateType=" + templateType +
           '}';
  }
}
