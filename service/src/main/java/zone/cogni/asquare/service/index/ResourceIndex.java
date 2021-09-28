package zone.cogni.asquare.service.index;

import java.util.Objects;

public class ResourceIndex {
  private final String graph;
  private final String uri;
  private final String type;
  private final String index;

  public ResourceIndex(String graph, String uri, String type, String index) {
    this.graph = graph;
    this.uri = uri;
    this.type = type;
    this.index = index;
  }

  public static ResourceIndex create(String graph, String uri, String type, String index) {
    return new ResourceIndex(graph, uri, type, index);
  }

  public String getGraph() {
    return graph;
  }

  public String getIndex() {
    return index;
  }

  public String getUri() {
    return uri;
  }

  public String getType() {
    return type;
  }

  @Override
  public String toString() {
    return "ResourceIndex{" +
           "graph='" + graph + '\'' +
           ", uri='" + uri + '\'' +
           ", type='" + type + '\'' +
           ", index='" + index + '\'' +
           '}';
  }
}
