package zone.cogni.asquare.applicationprofile.model.basic.def;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;
import io.vavr.control.Option;
import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.rules.Extra;
import zone.cogni.asquare.applicationprofile.rules.PropertyValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BasicAttributeDef extends ApplicationProfileDef.AttributeDef {
  @JsonIgnore
  private ApplicationProfileDef.TypeDef type;

  private String uri;
  private String attributeId;

  private Extra extra = new Extra();
  private List<Rule> rules = new ArrayList<>();

  @Override
  public String getUri() {
    return uri;
  }

  @Override
  public void setUri(String uri) {
    this.uri = uri;
  }

  @Override
  public ApplicationProfileDef.TypeDef getTypeDef() {
    return type;
  }

  @Override
  public void setTypeDef(ApplicationProfileDef.TypeDef type) {
    this.type = type;
  }

  @Override
  public String getAttributeId() {
    return attributeId;
  }

  @Override
  public void setAttributeId(String attributeId) {
    this.attributeId = attributeId;
  }

  @Override
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

  @Override
  public <T extends Rule> Option<T> getRule(Class<T> type) {
    List<T> rules = getRules(type);
    Preconditions.checkState(rules.size() <= 1, "Found more than one rule of type " + type.getSimpleName());

    return rules.isEmpty() ? Option.none() : Option.some(rules.get(0));
  }

  @Override
  public <T extends Rule> List<T> getRules(Class<T> type) {
    Stream<Rule> ruleStream = getRules().stream()
            .filter(rule -> Objects.equals(rule.getClass(), type));
    return (List<T>) ruleStream.collect(Collectors.toList());
  }

  @Override
  public List<Rule> getRules() {
    return Collections.unmodifiableList(rules);
  }

  @Override
  public void setRules(List<Rule> rules) {
    Objects.requireNonNull(rules);
    this.rules = rules;
  }

  @Override
  public void addRule(Rule rule) {
    rules.add(rule);
  }

  @Override
  public void removeRule(Rule rule) {
    rules.remove(rule);
  }

  @Override
  public Set<ApplicationProfileDef.AttributeDef> getRootDefinitions() {
    return Collections.singleton(this);
  }
}
