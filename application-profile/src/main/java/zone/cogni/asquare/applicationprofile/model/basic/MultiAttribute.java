package zone.cogni.asquare.applicationprofile.model.basic;

import com.google.common.base.Preconditions;
import io.vavr.control.Option;
import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.basic.def.ApplicationProfileDef;
import zone.cogni.asquare.applicationprofile.rules.Extra;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class MultiAttribute extends ApplicationProfile.Attribute {

  private final MultiType type;
  private final String attributeId;
  private final List<ApplicationProfile.Attribute> attributes;

  public MultiAttribute(MultiType type, String attributeId) {
    this.type = type;
    this.attributeId = attributeId;
    attributes = calculateAttributes();

    validate();
  }

  private List<ApplicationProfile.Attribute> calculateAttributes() {
    return type.getTypes().stream()
            .filter(t -> t.hasAttribute(attributeId))
            .map(t -> t.getAttribute(attributeId))
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
  public ApplicationProfile.Type getType() {
    return type;
  }

  @Override
  public String getAttributeId() {
    return attributeId;
  }

  @Override
  public Extra getExtra() {
    throw new UnsupportedOperationException("Extra data is only relevant in a serialization context.");
  }

  @Override
  public <T extends Rule> Option<T> getRule(Class<T> type) {
    List<T> rules = getRules(type);
    // TODO fix if multiple rules are found !!
    if (rules.size() > 1) throw new IllegalStateException("Many rules for rule " + type.getName() + " on attribute " + attributeId + " (on " + getType().getClassIds() + ")");

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
  public Set<ApplicationProfileDef.AttributeDef> getAttributeDef() {
    return attributes.stream()
            .flatMap(attribute -> attribute.getAttributeDef().stream())
            .collect(Collectors.toSet());
  }
}
