package zone.cogni.asquare.cube.jsondiff;


import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Configuration implements JsonSorter {

  private boolean preserveArrayOrder;
  private Set<String> propertiesWithDeviatingValues = new HashSet<>();
  private JsonSorter jsonSorter;

  public boolean isPreserveArrayOrder() {
    return preserveArrayOrder;
  }

  public void setPreserveArrayOrder(boolean preserveArrayOrder) {
    this.preserveArrayOrder = preserveArrayOrder;
  }

  public Set<String> getPropertiesWithDeviatingValues() {
    return propertiesWithDeviatingValues;
  }

  public void setPropertiesWithDeviatingValues(Set<String> propertiesWithDeviatingValues) {
    this.propertiesWithDeviatingValues = propertiesWithDeviatingValues;
  }

  public JsonSorter getJsonSorter() {
    return jsonSorter;
  }

  public void setJsonSorter(JsonSorter jsonSorter) {
    this.jsonSorter = jsonSorter;
  }

  @Override
  public Function<ObjectNode, String> getId() {
    return jsonSorter.getId();
  }

  @Override
  public BiFunction<ObjectNode, ObjectNode, Boolean> getComparator() {
    return jsonSorter.getComparator();
  }
}
