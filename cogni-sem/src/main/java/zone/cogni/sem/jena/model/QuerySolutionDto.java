package zone.cogni.sem.jena.model;

import java.util.Map;

public class QuerySolutionDto {

  private Map<String, RdfNodeDto> nodes;

  public Map<String, RdfNodeDto> getNodes() {
    return nodes;
  }

  public void setNodes(Map<String, RdfNodeDto> nodes) {
    this.nodes = nodes;
  }

  public String getProperty(String key) {
    if (!nodes.containsKey(key)) {
      return null;
    }
    return nodes.get(key).getStringValue();
  }

}
