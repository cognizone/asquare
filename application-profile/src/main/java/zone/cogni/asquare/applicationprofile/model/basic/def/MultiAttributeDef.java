package zone.cogni.asquare.applicationprofile.model.basic.def;

import com.google.common.base.Preconditions;
import io.vavr.control.Option;
import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.rules.Extra;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class MultiAttributeDef extends ApplicationProfileDef.AttributeDef {

  private final MultiTypeDef type;
  private final String attributeId;
  private final List<ApplicationProfileDef.AttributeDef> attributes;

  public MultiAttributeDef(MultiTypeDef type, String attributeId) {
    this.type = type;
    this.attributeId = attributeId;
    attributes = calculateAttributes();

    validate();
  }

  private List<ApplicationProfileDef.AttributeDef> calculateAttributes() {
    return type.getTypes().stream()
            .filter(t -> t.hasAttributeDef(attributeId))
            .map(t -> t.getAttributeDef(attributeId))
            .distinct()
            .collect(Collectors.toList());
  }

  private void validate() {
    // all uris are same!
    attributes.stream()
            .map(attribute -> attribute.getUri())
            .reduce((uri1, uri2) -> {
              Preconditions.checkState(Objects.equals(uri1, uri2));
              return uri1;
            });
  }

  @Override
  public String getUri() {
    return attributes.get(0).getUri();
  }

  @Override
  public void setUri(String uri) {
    throw new UnsupportedOperationException("MultiAttribute is readonly");
  }

  @Override
  public ApplicationProfileDef.TypeDef getTypeDef() {
    return type;
  }

  @Override
  public void setTypeDef(ApplicationProfileDef.TypeDef type) {
    throw new UnsupportedOperationException("MultiAttribute is readonly");
  }

  @Override
  public String getAttributeId() {
    return attributeId;
  }

  @Override
  public void setAttributeId(String attributeId) {
    throw new UnsupportedOperationException("MultiAttribute is readonly");
  }

  @Override
  public Extra getExtra() {
    return attributes.stream()
              .map(attribute -> attribute.getExtra())
              .findFirst()
              .orElseThrow(IllegalStateException::new);
  }

  @Override
  public void setExtra(Extra extra) {
    throw new UnsupportedOperationException("MultiAttribute is readonly");
  }

  @Override
  public void addExtra(String property, String value) {
    throw new UnsupportedOperationException("MultiAttribute is readonly");
  }

  @Override
  public void clearExtra() {
    throw new UnsupportedOperationException("MultiAttribute is readonly");
  }

  @Override
  public <T extends Rule> Option<T> getRule(Class<T> type) {
    List<T> rules = getRules(type);
    // TODO fix if multiple rules are found !!
    if (rules.size() > 1) throw new IllegalStateException("Many rules for rule " + type.getName() + " on attribute " + attributeId + " (on " + getTypeDef().getClassId() + ")");

    return rules.isEmpty() ? Option.none() : Option.of(rules.get(0));
  }

  @Override
  public <T extends Rule> List<T> getRules(Class<T> type) {
    return attributes.stream()
            .flatMap(t -> t.getRules(type).stream())
            .collect(Collectors.toList());
  }

  @Override
  public List<Rule> getRules() {
    return attributes.stream()
            .flatMap(t -> t.getRules().stream())
            .collect(Collectors.toList());
  }

  @Override
  public void setRules(List<Rule> rules) {
    throw new UnsupportedOperationException("MultiAttribute is readonly");
  }

  @Override
  public void addRule(Rule rule) {
    throw new UnsupportedOperationException("MultiAttribute is readonly");
  }

  @Override
  public void removeRule(Rule rule) {
    throw new UnsupportedOperationException("MultiAttribute is readonly");
  }

  @Override
  public Set<ApplicationProfileDef.AttributeDef> getRootDefinitions() {
    return attributes.stream()
            .flatMap(attribute -> attribute.getRootDefinitions().stream())
            .collect(Collectors.toSet());
  }
}
