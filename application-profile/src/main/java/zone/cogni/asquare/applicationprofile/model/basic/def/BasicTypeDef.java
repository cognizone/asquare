package zone.cogni.asquare.applicationprofile.model.basic.def;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;
import io.vavr.control.Option;
import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.rules.Extra;
import zone.cogni.asquare.applicationprofile.rules.PropertyValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BasicTypeDef extends ApplicationProfileDef.TypeDef {

  @JsonIgnore
  private ApplicationProfileDef applicationProfile;

  private String classId;
  private Extra extra = new Extra();
  private Map<String, ApplicationProfileDef.AttributeDef> attributes = new TreeMap<>();
  private List<Rule> rules = new ArrayList<>();

  @Override
  public ApplicationProfileDef getApplicationProfileDef() {
    return applicationProfile;
  }

  @Override
  public void setApplicationProfileDef(ApplicationProfileDef applicationProfile) {
    this.applicationProfile = applicationProfile;
  }

  @Override
  public String getClassId() {
    return classId;
  }

  @Override
  public void setClassId(String classId) {
    this.classId = classId;
  }

  @Override
  public boolean hasAttributeDef(String attributeId) {
    return attributes.containsKey(attributeId);
  }

  @Override
  public ApplicationProfileDef.AttributeDef getAttributeDef(String attributeId) {
    ApplicationProfileDef.AttributeDef attribute = attributes.get(attributeId);
    Objects.requireNonNull(attribute, () -> "Invalid attribute. '" + attributeId + "' does not exist on '" + classId + "'.");

    return attribute;
  }

  @Override
  public Option<ApplicationProfileDef.AttributeDef> getAttributeDefByUri(String uri) {
    Objects.requireNonNull(uri);

    return Option.ofOptional(attributes.values().stream()
            .filter(attribute -> Objects.equals(attribute.getUri(), uri))
            .findFirst());
  }

  @Override
  public Map<String, ApplicationProfileDef.AttributeDef> getAttributeDefs() {
    return Collections.unmodifiableMap(attributes);
  }

  @Override
  public Collection<String> getAttributeIds() {
    return getAttributeDefs().keySet();
  }

  @Override
  public void setAttributeDefs(Map<String, ApplicationProfileDef.AttributeDef> attributes) {
    Objects.requireNonNull(attributes, "attributes cannot be null");

    attributes.forEach((key, attribute) -> {
      attribute.setAttributeId(key);
      ensureTypeIsSet(attribute);
    });
    this.attributes.forEach(attributes::put);
  }

  @Override
  public void addAttributeDef(ApplicationProfileDef.AttributeDef attribute) {
    if (attributes.containsKey(attribute.getAttributeId()))
      throw new IllegalStateException("attribute '" + attribute.getAttributeId() + "' used more than once on type " + classId);

    ensureTypeIsSet(attribute);
    attributes.put(attribute.getAttributeId(), attribute);
  }

  @Override
  @JsonIgnore
  public Extra getExtra() {
    return extra;
  }

  @Override
  public void setExtra(Extra extra) {
    Preconditions.checkNotNull(extra);
    this.extra = extra;
  }

  @Override
  public void addExtra(String property, String value) {
    List<PropertyValue> result = extra.getValue();
    result.add(new PropertyValue(property, value));

    extra.setValue(result);
  }

  @Override
  public void clearExtra() {
    this.extra = new Extra();
  }

  public <T extends Rule> Option<T> getRule(Class<T> type) {
    List<T> rules = getRules(type);
    Preconditions.checkState(rules.size() <= 1, "Found more than one rule of type " + type.getSimpleName());

    return rules.isEmpty() ? Option.none() : Option.some(rules.get(0));
  }

  public <T extends Rule> List<T> getRules(Class<T> type) {
    Stream<Rule> ruleStream = getRules().stream()
            .filter(rule -> Objects.equals(rule.getClass(), type));
    return (List<T>) ruleStream.collect(Collectors.toList());
  }

  public List<Rule> getRules() {
    return rules;
  }

  public void setRules(List<Rule> rules) {
    Objects.requireNonNull(rules);
    this.rules = rules;
  }

  public void addRule(Rule rule) {
    rules.add(rule);
  }

  public void removeRule(Rule rule) {
    rules.remove(rule);
  }

  @Override
  public Set<ApplicationProfileDef.TypeDef> getRootDefinitions() {
    return Collections.singleton(this);
  }

  private void ensureTypeIsSet(ApplicationProfileDef.AttributeDef attribute) {
    Preconditions.checkState(attribute.getTypeDef() == null || attribute.getTypeDef() == this);
    if (attribute.getTypeDef() == null) attribute.setTypeDef(this);
  }


}
