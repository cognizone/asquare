package zone.cogni.asquare.cube.convertor.data2shacl;

import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Configuration {
  private String shapesPrefix;
  private String shapesNamespace;

  private boolean includeShapesGraph;

  private boolean reportPossibleIssues;

  private List<String> ignoredClasses;

  private Map<Set<String>, String> typeTranslations;

  public Configuration() {
  }

  public Configuration(String shapesPrefix, String shapesNamespace) {
    this.shapesPrefix = shapesPrefix;
    this.shapesNamespace = shapesNamespace;
  }

  public void setShapesNamespace(String prefix, String namespace) {
    if (!namespace.endsWith("/") && !namespace.endsWith("#"))
      throw new RuntimeException("invalid namespace, please make it end on '#' or '/'");

    setShapesPrefix(prefix);
    setShapesNamespace(namespace);
  }

  public String getShapesPrefix() {
    return shapesPrefix;
  }

  public void setShapesPrefix(String shapesPrefix) {
    this.shapesPrefix = shapesPrefix;
  }

  public String getShapesNamespace() {
    return shapesNamespace;
  }

  public void setShapesNamespace(String shapesNamespace) {
    this.shapesNamespace = shapesNamespace;
  }

  public boolean isIncludeShapesGraph() {
    return includeShapesGraph;
  }

  public void setIncludeShapesGraph(boolean includeShapesGraph) {
    this.includeShapesGraph = includeShapesGraph;
  }

  public boolean isReportPossibleIssues() {
    return reportPossibleIssues;
  }

  public void setReportPossibleIssues(boolean reportPossibleIssues) {
    this.reportPossibleIssues = reportPossibleIssues;
  }

  public List<String> getIgnoredClasses() {
    return ListUtils.emptyIfNull(ignoredClasses);
  }

  public void setIgnoredClasses(List<String> ignoredClasses) {
    this.ignoredClasses = ignoredClasses;
  }

  public Map<Set<String>, String> getTypeTranslations() {
    return MapUtils.emptyIfNull(typeTranslations);
  }

  public void setTypeTranslations(Map<Set<String>, String> typeTranslations) {
    this.typeTranslations = typeTranslations;
  }

  public String getTypeTranslation(Set<String> types) {
    if (typeTranslations == null) return null;
    return typeTranslations.get(types);
  }

  public void addTypeTranslation(Set<String> types, String type) {
    if (typeTranslations == null) typeTranslations = new HashMap<>();

    typeTranslations.put(types, type);
  }
}
