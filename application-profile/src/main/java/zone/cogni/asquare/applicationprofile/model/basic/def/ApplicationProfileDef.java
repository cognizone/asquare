package zone.cogni.asquare.applicationprofile.model.basic.def;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.vavr.control.Option;
import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.rules.Extra;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public abstract class ApplicationProfileDef {
  public static ApplicationProfileDef newInstance() {
    return new BasicApplicationProfileDef();
  }

  public abstract String getUri();

  public abstract void setUri(String uri);

  @JsonIgnore
  public abstract Extra getExtra();

  public abstract void setExtra(Extra extra);

  public abstract void addExtra(String property, String value);

  @JsonIgnore
  public abstract List<ApplicationProfileDef> getImports();

  public abstract void setImports(List<ApplicationProfileDef> applicationProfiles);

  public abstract void addImport(ApplicationProfileDef applicationProfile);

  public abstract boolean hasTypeDef(String typeId);

  public abstract TypeDef getTypeDef(String typeId);

  public abstract Map<String, TypeDef> getTypeDefs();

  public abstract void setTypeDefs(Map<String, TypeDef> types);

  public abstract void addTypeDef(TypeDef type);

  @JsonIgnore
  public ApplicationProfileDef getTopDefinition() {
    return getParentDefinition() == null ? this
                                         : getParentDefinition().getTopDefinition();
  }

  @JsonIgnore
  // TODO REVISE THIS method, method fails in case of MultiApplicationProfileDef
  public List<ApplicationProfileDef> getApplicationProfileDefs() {
    List<ApplicationProfileDef> result = new ArrayList<>();
    getApplicationProfileDefs(result);
    return result;
  }

  protected void getApplicationProfileDefs(List<ApplicationProfileDef> result) {
    // TODO fix multiple imports
    result.add(this);
    getImports().forEach(imp -> imp.getApplicationProfileDefs(result));
  }

  @JsonIgnore
  public abstract ApplicationProfileDef getParentDefinition();

  public abstract void setParentDefinition(ApplicationProfileDef parentApplicationProfile);

  @JsonIgnore
  public abstract ApplicationProfileDef getRootDefinition();

  @JsonIgnore
  public abstract Set<Class<?>> getInternalClassChain();

  @Override
  public int hashCode() {
    return Objects.hash(getUri());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ApplicationProfileDef)) return false;

    return getRootDefinition() == ((ApplicationProfileDef) o).getRootDefinition();
  }

  public interface RuleContainer {

    <T extends Rule> Option<T> getRule(Class<T> type);

    <T extends Rule> List<T> getRules(Class<T> type);

    List<Rule> getRules();

    void setRules(List<Rule> rules);

    void addRule(Rule rule);

    void removeRule(Rule rule);

  }

  public interface ExtraContainer {

    @JsonIgnore
    Extra getExtra();

    void setExtra(Extra extra);

    void addExtra(String property, String value);

    void clearExtra();
  }

  /**
   *
   */
  public abstract static class TypeDef implements RuleContainer, ExtraContainer {
    public static TypeDef newInstance() {
      return new BasicTypeDef();
    }

    @JsonIgnore
    public String getDescription() {
      return "(" + getClass().getSimpleName() + ") "
             + getClassId();
    }

    public abstract ApplicationProfileDef getApplicationProfileDef();

    public abstract void setApplicationProfileDef(ApplicationProfileDef applicationProfile);

    public abstract String getClassId();

    public abstract void setClassId(String classId);

    public abstract boolean hasAttributeDef(String attributeId);

    public abstract AttributeDef getAttributeDef(String attributeId);

    public abstract Option<AttributeDef> getAttributeDefByUri(String uri);

    public abstract Map<String, AttributeDef> getAttributeDefs();

    @JsonIgnore
    public abstract Collection<String> getAttributeIds();

    public abstract void setAttributeDefs(Map<String, AttributeDef> attributes);

    public abstract void addAttributeDef(AttributeDef attribute);

    @JsonIgnore
    public abstract Set<TypeDef> getRootDefinitions();

    private boolean hasSameReferences(Set<TypeDef> one, Set<TypeDef> two) {
      if (one.size() != two.size()) return false;
      return two.stream().allMatch(element -> hasReference(one, element));
    }

    private boolean hasReference(Collection<TypeDef> collection, TypeDef reference) {
      return collection.stream().anyMatch(element -> element == reference);
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof TypeDef)) return false;

      return hasSameReferences(getRootDefinitions(), ((TypeDef) o).getRootDefinitions());
    }

    public int hashCode() {
      return Objects.hash(getClassId());
    }
  }

  /**
   *
   */
  public abstract static class AttributeDef implements RuleContainer, ExtraContainer {
    public static AttributeDef newInstance() {
      return new BasicAttributeDef();
    }

    public abstract String getUri();

    public abstract void setUri(String uri);

    public abstract TypeDef getTypeDef();

    public abstract void setTypeDef(TypeDef type);

    public abstract String getAttributeId();

    public abstract void setAttributeId(String attributeId);

    @JsonIgnore
    public abstract Set<AttributeDef> getRootDefinitions();

    private boolean hasSameReferences(Set<AttributeDef> one, Set<AttributeDef> two) {
      if (one.size() != two.size()) return false;
      return two.stream().allMatch(element -> hasReference(one, element));
    }

    private boolean hasReference(Collection<AttributeDef> collection, AttributeDef reference) {
      return collection.stream().anyMatch(element -> element == reference);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof AttributeDef)) return false;

      return hasSameReferences(getRootDefinitions(), ((AttributeDef) o).getRootDefinitions());
    }

    @Override
    public int hashCode() {
      return Objects.hash(getAttributeId());
    }
  }
}
