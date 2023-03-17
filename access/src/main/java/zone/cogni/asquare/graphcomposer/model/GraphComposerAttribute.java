package zone.cogni.asquare.graphcomposer.model;

import org.apache.commons.lang3.StringUtils;
import zone.cogni.asquare.graphcomposer.GraphComposerUtils;

import java.util.Map;

public class GraphComposerAttribute {

  private String name;
  private String objectType;
  private String predicate;
  private String object;
  private String unique;
  private String replace;

  private String versionParameter;
  private String versionPredicate;
  private String versionLoadPattern;

  public String getVersionParameter() {
    return versionParameter;
  }

  public void setVersionParameter(String versionParameter) {
    this.versionParameter = versionParameter;
  }

  public String getVersionPredicate() {
    return versionPredicate;
  }

  public void setVersionPredicate(String versionPredicate) {
    this.versionPredicate = versionPredicate;
  }

  public boolean hasVersionPredicate() {
    return StringUtils.isNoneBlank(versionPredicate, versionParameter, versionLoadPattern);
  }

  public String getVersionPredicate(Map<String, String> context) {
    return GraphComposerUtils.replace(versionPredicate, context);
  }

  public String getVersionLoadPattern() {
    return versionLoadPattern;
  }

  public void setVersionLoadPattern(String versionLoadPattern) {
    this.versionLoadPattern = versionLoadPattern;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getObjectType() {
    return objectType;
  }

  public void setObjectType(String objectType) {
    this.objectType = objectType;
  }

  public String getObjectType(Map<String, String> context) {
    return GraphComposerUtils.replace(objectType, context);
  }

  public String getPredicate() {
    return predicate;
  }

  public void setPredicate(String predicate) {
    this.predicate = predicate;
  }

  public String getPredicate(Map<String, String> context) {
    return GraphComposerUtils.replace(predicate, context);
  }

  public String getObject() {
    return object;
  }

  public void setObject(String object) {
    this.object = object;
  }

  public String getObject(Map<String, String> context) {
    return GraphComposerUtils.replace(object, context);
  }

  public String getUnique() {
    return unique;
  }

  public void setUnique(String unique) {
    this.unique = unique;
  }

  public Boolean getUnique(Map<String, String> context) {
    return StringUtils.equalsIgnoreCase(GraphComposerUtils.replace(unique, context), "true");
  }

  public void setReplace(String replace) {
    this.replace = replace;
  }

  public String getReplace() {
    return replace;
  }

  public boolean isReplace() {
    return Boolean.parseBoolean(replace);
  }

  public String toString() {
    String str = "[";
    if (StringUtils.isNotBlank(predicate)) {
      str += " predicate: \"" + predicate + "\",";
    }
    if (StringUtils.isNotBlank(object)) {
      str += " object: \"" + object + "\",";
    }
    if (StringUtils.isNotBlank(objectType)) {
      str += " objectType: \"" + objectType + "\",";
    }
    if (StringUtils.isNotBlank(unique)) {
      str += " unique: \"" + unique + "\",";
    }
    return StringUtils.removeEnd(str, ",") + " ]";
  }

}
