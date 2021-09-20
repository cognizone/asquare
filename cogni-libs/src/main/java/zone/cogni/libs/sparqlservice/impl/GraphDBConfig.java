package zone.cogni.libs.sparqlservice.impl;

public class GraphDBConfig extends Config {

  public GraphDBConfig() {}

  public GraphDBConfig(Config config) {
    setUrl(config.getUrl());
    setUser(config.getUser());
    setPassword(config.getPassword());
  }

  private String repository;

  public String getRepository() {
    return repository;
  }

  public void setRepository(String repository) {
    this.repository = repository;
  }

  public String getSparqlEndpoint() {
    return getUrl() + "/repositories/" + getRepository();
  }

  public String getSparqlUpdateEndpoint() {
    return getSparqlEndpoint() + "/statements";
  }

  public String getImportTextEndpoint() {
    return getUrl() + "/rest/data/import/upload/" + getRepository() + "/text";
  }

}
