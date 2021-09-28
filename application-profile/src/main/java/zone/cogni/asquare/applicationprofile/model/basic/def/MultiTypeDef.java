package zone.cogni.asquare.applicationprofile.model.basic.def;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;
import io.vavr.control.Option;
import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.rules.Extra;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MultiTypeDef extends ApplicationProfileDef.TypeDef {

  public static MultiTypeDef newInstance(MultiApplicationProfileDef applicationProfile, Stream<ApplicationProfileDef.TypeDef> types) {
    return new MultiTypeDef(applicationProfile, types.collect(Collectors.toList()));
  }

  @JsonIgnore
  private final ApplicationProfileDef applicationProfile;
  private final List<ApplicationProfileDef.TypeDef> types;

  private MultiTypeDef(MultiApplicationProfileDef applicationProfile, List<ApplicationProfileDef.TypeDef> types) {
    Preconditions.checkArgument(!types.isEmpty());

    this.types = types;
    this.applicationProfile = applicationProfile;

    validate();
  }

  private void validate() {
    // all class ids are same!
    getClassId();
  }

  protected List<ApplicationProfileDef.TypeDef> getTypes() {
    return Collections.unmodifiableList(types);
  }

  @Override
  public ApplicationProfileDef getApplicationProfileDef() {
    return applicationProfile;
  }

  @Override
  public void setApplicationProfileDef(ApplicationProfileDef applicationProfile) {
    throw new UnsupportedOperationException("MultiType is readonly");
  }

  @Override
  public String getClassId() {
    return types.stream()
            .map(ApplicationProfileDef.TypeDef::getClassId)
            .reduce((classId1, classId2) -> {
              Preconditions.checkState(Objects.equals(classId1, classId2));
              return classId1;
            }).get();
  }

  @Override
  public void setClassId(String classId) {
    throw new UnsupportedOperationException("MultiType is readonly");
  }

  @Override
  public boolean hasAttributeDef(String attributeId) {
    return types.stream()
            .anyMatch(type -> type.hasAttributeDef(attributeId));
  }

  @Override
  public ApplicationProfileDef.AttributeDef getAttributeDef(String attributeId) {
    return types.stream()
            .filter(type -> type.hasAttributeDef(attributeId))
            .map(type -> new MultiAttributeDef(this, attributeId))
            .findFirst()
            .orElseThrow(() -> new NullPointerException("Invalid attribute. '" + attributeId + "' does not exist on type [" + getClassId() + "]."));
  }

  @Override
  public Option<ApplicationProfileDef.AttributeDef> getAttributeDefByUri(String uri) {
    Option<ApplicationProfileDef.AttributeDef> attributeDef = types.stream()
            .map(type -> type.getAttributeDefByUri(uri))
            .filter(Option::isDefined)
            .findFirst()
            .orElse(Option.none());

    return attributeDef.map(attr -> new MultiAttributeDef(this, attr.getAttributeId()));
  }

  @Override
  public Map<String, ApplicationProfileDef.AttributeDef> getAttributeDefs() {

    // find all attribute ids
    Set<String> attributeIds = types.stream()
            .flatMap(type -> type.getAttributeDefs().keySet().stream())
            .collect(Collectors.toSet());

    Map<String, ApplicationProfileDef.AttributeDef> result = new HashMap<>();

    // add all attributes to map
    attributeIds.stream()
            .map(attributeId -> new MultiAttributeDef(this, attributeId))
            .forEach(attribute -> result.put(attribute.getAttributeId(), attribute));

    return result;
  }

  @Override
  public Collection<String> getAttributeIds() {
    return getAttributeDefs().keySet();
  }

  @Override
  public void setAttributeDefs(Map<String, ApplicationProfileDef.AttributeDef> attributes) {
    throw new UnsupportedOperationException("MultiType is readonly");
  }

  @Override
  public void addAttributeDef(ApplicationProfileDef.AttributeDef attribute) {
    throw new UnsupportedOperationException("MultiType is readonly");
  }

  @Override
  public Extra getExtra() {
    return types.stream()
                .map(ApplicationProfileDef.ExtraContainer::getExtra)
                .findFirst()
                .orElseThrow(IllegalStateException::new);
  }

  @Override
  public void setExtra(Extra extra) {
    throw new UnsupportedOperationException("MultiType is readonly");
  }

  @Override
  public void addExtra(String property, String value) {
    throw new UnsupportedOperationException("MultiType is readonly");
  }

  @Override
  public void clearExtra() {
    throw new UnsupportedOperationException("MultiType is readonly");
  }

  @Override
  public <T extends Rule> Option<T> getRule(Class<T> type) {
    List<T> rules = getRules(type);

    Preconditions.checkState(rules.size() <= 1);
    return rules.isEmpty() ? Option.none()
                           : Option.of(rules.get(0));
  }

  @Override
  public <T extends Rule> List<T> getRules(Class<T> type) {
    return types.stream()
            .flatMap(t -> t.getRules(type).stream())
            .distinct()
            .collect(Collectors.toList());
  }

  @Override
  public List<Rule> getRules() {
    return types.stream()
            .flatMap(type -> type.getRules().stream())
            .distinct()
            .collect(Collectors.toList());
  }

  @Override
  public void setRules(List<Rule> rules) {
    throw new UnsupportedOperationException("MultiType is readonly");
  }

  @Override
  public void addRule(Rule rule) {
    throw new UnsupportedOperationException("MultiType is readonly");
  }

  @Override
  public void removeRule(Rule rule) {
    throw new UnsupportedOperationException("MultiType is readonly");
  }

  @Override
  public Set<ApplicationProfileDef.TypeDef> getRootDefinitions() {
    return types.stream()
            .flatMap(type -> type.getRootDefinitions().stream())
            .collect(Collectors.toSet());
  }

}
