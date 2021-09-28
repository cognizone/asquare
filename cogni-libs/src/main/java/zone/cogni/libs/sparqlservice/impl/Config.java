package zone.cogni.libs.sparqlservice.impl;

public class Config {
  private String url;
  private String user;
  private String password;
  private boolean graphCrudUseBasicAuth;

  public Config() {
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public boolean isGraphCrudUseBasicAuth() {
    return graphCrudUseBasicAuth;
  }

  public void setGraphCrudUseBasicAuth(boolean graphCrudUseBasicAuth) {
    this.graphCrudUseBasicAuth = graphCrudUseBasicAuth;
  }
}
