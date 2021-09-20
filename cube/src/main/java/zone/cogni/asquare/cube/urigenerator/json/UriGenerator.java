package zone.cogni.asquare.cube.urigenerator.json;

import java.util.List;

public class UriGenerator {

  private String id;
  private String uriSelector;
  private String variableSelector;
  private String uriTemplate;

  public UriGenerator() {
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getUriSelector() {
    return uriSelector;
  }

  public void setUriSelector(String uriSelector) {
    this.uriSelector = uriSelector;
  }

  public String getVariableSelector() {
    return variableSelector;
  }

  public void setVariableSelector(String variableSelector) {
    this.variableSelector = variableSelector;
  }

  public String getUriTemplate() {
    return uriTemplate;
  }

  public void setUriTemplate(String uriTemplate) {
    this.uriTemplate = uriTemplate;
  }

  public String getFullUriSelector() {
    if (uriSelector == null) return "";
    return uriSelector;
  }

  public String getFullVariableSelector() {
    if (variableSelector == null) return "";
    return variableSelector;
  }
}
