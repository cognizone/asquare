package zone.cogni.asquare.cube.convertor.data2shacl;

import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Configuration {
  private String shapesNamespace;

  private List<String> ignoredClasses = new ArrayList<>();

  private Map<Set<String>, String> typeTranslations = new HashMap<>();

  private List<String> prioritisedNamespaces = new ArrayList<>();

  public Configuration() {
  }

  public Configuration(String shapesNamespace) {
    this.shapesNamespace = shapesNamespace;
  }

  public String getShapesNamespace() {
    return shapesNamespace;
  }

  public void setShapesNamespace(String shapesNamespace) {
    this.shapesNamespace = shapesNamespace;
  }

  public List<String> getIgnoredClasses() {
    return ListUtils.emptyIfNull(ignoredClasses);
  }

  public void setIgnoredClasses(List<String> ignoredClasses) {
    Objects.requireNonNull(ignoredClasses, "ignoredClasses cannot be null");
    this.ignoredClasses = ignoredClasses;
  }

  public Map<Set<String>, String> getTypeTranslations() {
    return MapUtils.emptyIfNull(typeTranslations);
  }

  public void setTypeTranslations(Map<Set<String>, String> typeTranslations) {
    Objects.requireNonNull(typeTranslations, "typeTranslations cannot be null");
    this.typeTranslations = typeTranslations;
  }

  public String getTypeTranslation(Set<String> types) {
    return typeTranslations.get(types);
  }

  public void addTypeTranslation(Set<String> types, String type) {
    typeTranslations.put(types, type);
  }

  public List<String> getPrioritisedNamespaces() {
    return prioritisedNamespaces;
  }

  public void setPrioritisedNamespaces(List<String> prioritisedNamespaces) {
    this.prioritisedNamespaces = prioritisedNamespaces;
  }

  public int getPriorityIndex(String namespaceIri) {
    return prioritisedNamespaces.indexOf(namespaceIri);
  }
}
