package zone.cogni.asquare.applicationprofile.model.basic;

import com.google.common.base.Preconditions;
import io.vavr.control.Option;
import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.basic.def.ApplicationProfileDef;
import zone.cogni.asquare.applicationprofile.rules.Extra;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Class representing multiple non-disjoint types.
 * The types do not belong to each other's class hierarchy, i.e there is no super/sub class relationship between them
 */
public class MultiType extends ApplicationProfile.Type {

  static MultiType newInstance(Collection<BasicType> types) {
    return new MultiType(new ArrayList<>(types));
  }

  private final ApplicationProfile applicationProfile;
  private final List<BasicType> types;

  private MultiType(List<BasicType> types) {
    Preconditions.checkArgument(!types.isEmpty());

    this.types = types;
    this.applicationProfile = calculateApplicationProfile();
  }

  private ApplicationProfile calculateApplicationProfile() {
    Set<ApplicationProfileDef> applicationProfiles = types.stream()
            .map(type -> type.getApplicationProfile().getApplicationProfileDef())
            .collect(Collectors.toSet());

    Preconditions.checkState(applicationProfiles.size() == 1, "expected one top application profile");
    return new BasicApplicationProfile(applicationProfiles.stream().findFirst().get());
  }

  protected List<BasicType> getTypes() {
    return Collections.unmodifiableList(types);
  }

  @Override
  public ApplicationProfile getApplicationProfile() {
    return applicationProfile;
  }

  @Override
  public Set<String> getClassIds() {
    return types.stream()
            .flatMap(type -> type.getClassIds().stream())
            .collect(Collectors.toCollection(TreeSet::new));
  }

  @Override
  public Set<String> getSuperClassIds() {
    return types.stream()
            .flatMap(type -> type.getSuperClassIds().stream())
            .collect(Collectors.toCollection(TreeSet::new));
  }

  @Override
  public boolean hasAttribute(String attributeId) {
    return types.stream()
            .anyMatch(type -> type.hasAttribute(attributeId));
  }

  @Override
  public ApplicationProfile.Attribute getAttribute(String attributeId) {
    return types.stream()
            .filter(type -> type.hasAttribute(attributeId))
            .map(type -> new MultiAttribute(this, attributeId))
            .findFirst()
            .orElseThrow(() -> new NullPointerException("Invalid attribute. '" + attributeId + "' does not exist on multitype [" + String.join(", ", getClassIds()) + "]."));
  }

  @Override
  public Option<ApplicationProfile.Attribute> getAttributeByUri(String uri) {
    Option<ApplicationProfile.Attribute> attribute = types.stream()
            .map(type -> type.getAttributeByUri(uri))
            .filter(Option::isDefined)
            .findFirst()
            .orElse(Option.none());

    return attribute.map(attr -> new MultiAttribute(this, attr.getAttributeId()));
  }

  @Override
  public Map<String, ApplicationProfile.Attribute> getAttributes() {

    // find all attribute ids
    Set<String> attributeIds = types.stream()
            .flatMap(type -> type.getAttributes().keySet().stream())
            .collect(Collectors.toSet());

    Map<String, ApplicationProfile.Attribute> result = new HashMap<>();

    // add all attributes to map
    attributeIds.stream()
            .map(attributeId -> new MultiAttribute(this, attributeId))
            .forEach(attribute -> result.put(attribute.getAttributeId(), attribute));

    return result;
  }

  @Override
  public Extra getExtra() {
    throw new UnsupportedOperationException("Extra data is only relevant in a serialization context.");
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
  public Set<ApplicationProfileDef.TypeDef> getTypeDef() {
    return getTypes().stream()
            .flatMap(type -> type.getTypeDef().stream())
            .collect(Collectors.toSet());
  }

  /**
   * Must return true if this type conforms (complies with) the type definition of the other type
   * <p>
   * Conforms to/complies with type definition of other type
   * Is subclass of
   */
  @Override
  public boolean conformsTo(ApplicationProfile.Type otherType) {
    return types.stream().anyMatch(type -> type.conformsTo(otherType));
  }

}
