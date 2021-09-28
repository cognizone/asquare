package zone.cogni.asquare.graphcomposer.model;

import org.apache.commons.lang3.StringUtils;
import zone.cogni.asquare.graphcomposer.GraphComposerUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GraphComposerSubject {

  private final Map<String, String> values = new HashMap<>();

  private List<GraphComposerAttribute> attributes;

  public String getExcludeFromMapping() {
    return values.get("excludeFromMapping");
  }

  public void setExcludeFromMapping(String exists) {
    values.put("excludeFromMapping", exists);
  }

  public String getExists() {
    return values.get("exists");
  }

  public void setExists(String exists) {
    values.put("exists", exists);
  }

  public String getGraphType() {
    return values.get("graphType");
  }

  public void setGraphType(String graphType) {
    values.put("graphType", graphType);
  }

  public String getName() {
    return values.get("name");
  }

  public void setName(String name) {
    values.put("name", name);
  }

  public String getGraph() {
    return values.get("graph");
  }

  public void setGraph(String graph) {
    values.put("graph", graph);
  }

  public String getGraph(Map<String, String> context) {
    return GraphComposerUtils.replace(values.get("graph"), context);
  }

  public String getUri() {
    return values.get("uri");
  }

  public void setUri(String uri) {
    values.put("uri", uri);
  }

  public String getField(String fieldName) {
    return values.get(fieldName);
  }

  public String getUri(Map<String, String> context) {
    return GraphComposerUtils.replace(values.get("uri"), context);
  }

  public String getType() {
    return values.get("type");
  }

  public void setType(String type) {
    values.put("type", type);
  }

  public String getType(Map<String, String> context) {
    return GraphComposerUtils.replace(values.get("type"), context);
  }

  public String getExists(Map<String, String> context) {
    if(values.containsKey("exists")) {
      return GraphComposerUtils.replace(values.get("exists"), context);
    }
    return null;
  }

  public List<GraphComposerAttribute> getAttributes() {
    return attributes;
  }

  public void setAttributes(List<GraphComposerAttribute> attributes) {
    this.attributes = attributes;
  }

  public Optional<GraphComposerAttribute> getAttributeByName(String name) {
    return attributes.stream().filter(a->StringUtils.equals(name, a.getName())).findFirst();
  }

  public String toString() {
    String str = "(";

    for (String key : values.keySet()) {
      String value = values.get(key);
      if (StringUtils.isNotBlank(value)) {
        str += " " + key + ": \"" + value + "\",";
      }
    }
    str = StringUtils.removeEnd(str, ",");
    if (attributes != null) {
      str += ", attributes:";
      for (GraphComposerAttribute attr : attributes) {
        str += " " + attr.toString() + ",";
      }
    }
    return StringUtils.removeEnd(str, ",") + " )";
  }
}
