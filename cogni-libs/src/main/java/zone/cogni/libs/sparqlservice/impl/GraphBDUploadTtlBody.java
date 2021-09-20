package zone.cogni.libs.sparqlservice.impl;

public class GraphBDUploadTtlBody {

  private String name;
  private String context;
  private String data;

  public String getFormat() {
    return "text/turtle";
  }

  public String getName() {
    return name;
  }

  /**
   *
   * @param name name of the file
   */
  public GraphBDUploadTtlBody setName(String name) {
    this.name = name;
    return this;
  }

  public String getContext() {
    return context;
  }

  /**
   *
   * @param context graph uri
   */
  public GraphBDUploadTtlBody setContext(String context) {
    this.context = context;
    return this;
  }

  public String getData() {
    return data;
  }

  /**
   *
   * @param data rdf in ttl format
   */
  public GraphBDUploadTtlBody setData(String data) {
    this.data = data;
    return this;
  }

}
