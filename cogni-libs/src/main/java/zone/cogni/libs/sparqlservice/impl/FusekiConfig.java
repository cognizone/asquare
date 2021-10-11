package zone.cogni.libs.sparqlservice.impl;

import org.apache.commons.lang3.StringUtils;

public class FusekiConfig extends Config {

  private String updateUrl;
  private String queryUrl;
  private String graphStoreUrl;
  private String overwriteTurtleMimeType;

  public static FusekiConfig from(Config config) {
    FusekiConfig fusekiConfig = new FusekiConfig();
    fusekiConfig.setUrl(config.getUrl());
    fusekiConfig.setUser(config.getUser());
    fusekiConfig.setPassword(config.getPassword());
    return fusekiConfig;
  }

  public String getUpdateUrl() {
    return getServiceUrl(updateUrl, "/update");
  }

  public FusekiConfig setUpdateUrl(String updateUrl) {
    this.updateUrl = updateUrl;
    return this;
  }

  public String getQueryUrl() {
    return getServiceUrl(queryUrl, "/query");
  }

  public FusekiConfig setQueryUrl(String queryUrl) {
    this.queryUrl = queryUrl;
    return this;
  }

  public String getGraphStoreUrl() {
    return getServiceUrl(graphStoreUrl, "/data");
  }

  public FusekiConfig setGraphStoreUrl(String graphStoreUrl) {
    this.graphStoreUrl = graphStoreUrl;
    return this;
  }

  public String getTurtleMimeType() {
    return StringUtils.defaultIfBlank(overwriteTurtleMimeType, "text/turtle");
  }

  public String getOverwriteTurtleMimeType() {
    return overwriteTurtleMimeType;
  }

  public FusekiConfig setOverwriteTurtleMimeType(String overwriteTurtleMimeType) {
    this.overwriteTurtleMimeType = overwriteTurtleMimeType;
    return this;
  }

  private String getServiceUrl(String fixedUrl, String defaultSuffix) {
    if (StringUtils.isNotBlank(fixedUrl)) return fixedUrl;
    else if (StringUtils.isNotBlank(getUrl())) return getUrl() + defaultSuffix;
    else return null;
  }
}
