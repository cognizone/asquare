package zone.cogni.asquare.cube.convertor.data2shacl;

import java.util.List;

public class Configuration {
  private String shapesPrefix;
  private String shapesNamespace;

  private boolean includeShapesGraph;

  private List<String> ignoredClasses;

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

  public List<String> getIgnoredClasses() {
    return ignoredClasses;
  }

  public void setIgnoredClasses(List<String> ignoredClasses) {
    this.ignoredClasses = ignoredClasses;
  }
}
