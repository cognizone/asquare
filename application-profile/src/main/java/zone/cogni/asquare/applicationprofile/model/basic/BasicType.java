package zone.cogni.asquare.applicationprofile.model.basic;

import com.google.common.base.Preconditions;
import io.vavr.control.Option;
import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.basic.def.ApplicationProfileDef;
import zone.cogni.asquare.applicationprofile.rules.Extra;
import zone.cogni.asquare.applicationprofile.rules.SubClassOf;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class BasicType extends ApplicationProfile.Type {
  private final ApplicationProfileDef.TypeDef type;

  BasicType(ApplicationProfileDef.TypeDef type) {
    this.type = type;
  }

  @Override
  public ApplicationProfile getApplicationProfile() {
    return new BasicApplicationProfile(type.getApplicationProfileDef());
  }

  @Override
  public Set<String> getClassIds() {
    return Collections.singleton(type.getClassId());
  }

  @Override
  public Set<String> getSuperClassIds() {
    return getSuperTypeDefinitions().stream()
            .map(ApplicationProfileDef.TypeDef::getClassId)
            .collect(Collectors.toCollection(TreeSet::new));
  }


  @Override
  public boolean hasAttribute(String attributeId) {
    return getSuperTypeDefinitions().stream()
            .anyMatch(type -> type.hasAttributeDef(attributeId));
  }

  @Override
  public ApplicationProfile.Attribute getAttribute(String attributeId) {
    ApplicationProfileDef.AttributeDef attribute = getSuperTypeDefinitions().stream()
            .filter(type -> type.hasAttributeDef(attributeId))
            .map(type -> type.getAttributeDef(attributeId))
            .findFirst()
            .orElseThrow(() -> new NullPointerException("Invalid attribute. '" + attributeId + "' does not exist on singletype [" + String.join(", ", getClassIds()) + "]."));

    return new BasicAttribute(attribute);
  }

  @Override
  public Option<ApplicationProfile.Attribute> getAttributeByUri(String uri) {
    Option<ApplicationProfileDef.AttributeDef> attribute = getSuperTypeDefinitions().stream()
            .map(type -> type.getAttributeDefByUri(uri))
            .filter(Option::isDefined)
            .findFirst()
            .orElse(Option.none());

    return attribute.map(BasicAttribute::new);
  }

  @Override
  // todo is this ok? in MultiType a MultiAttribute is used?
  public Map<String, ApplicationProfile.Attribute> getAttributes() {
    Map<String, ApplicationProfile.Attribute> result = new HashMap<>();
    getSuperTypeDefinitions().forEach(type -> {
      type.getAttributeDefs()
              .forEach((k, v) -> {
                ApplicationProfile.Attribute previous = result.put(k, new BasicAttribute(v));
                Preconditions.checkState(previous == null, "Attribute with id '" + k + "' defined more than once on singletype [" + String.join(", ", getClassIds()) + "].");
              });
    });
    return result;
  }

  @Override
  public Extra getExtra() {
    return type.getExtra();
  }

  public <T extends Rule> Option<T> getRule(Class<T> type) {
    List<T> rules = getRules(type);

    Preconditions.checkState(rules.size() <= 1);
    return rules.isEmpty() ? Option.none()
                           : Option.of(rules.get(0));
  }

  @Override
  public <T extends Rule> List<T> getRules(Class<T> type) {
    return getSuperTypeDefinitions().stream()
            .flatMap(t -> t.getRules(type).stream())
            .distinct()
            .collect(Collectors.toList());
  }

  @Override
  public List<Rule> getRules() {
    return getSuperTypeDefinitions().stream()
            .flatMap(t -> t.getRules().stream())
            .distinct()
            .collect(Collectors.toList());
  }

  @Override
  public Set<ApplicationProfileDef.TypeDef> getTypeDef() {
    return Collections.singleton(type);
  }

  /**
   * Must return true if this type conforms (complies with) the type definition of the other type
   * <p>
   * Conforms to/complies with type definition of other type
   * Is subclass of
   */
  @Override
  public boolean conformsTo(ApplicationProfile.Type otherType) {
    return otherType.getTypeDef().stream().allMatch(otherTypeDefinition -> conformsTo(otherTypeDefinition));
  }

  boolean conformsTo(ApplicationProfileDef.TypeDef otherTypeDefinition) {
    return getSuperTypeDefinitions().contains(otherTypeDefinition);
  }

  private Collection<ApplicationProfileDef.TypeDef> getSuperTypeDefinitions() {
    Set<ApplicationProfileDef.TypeDef> result = new HashSet<>();
    getSuperTypeDefinition(result, type);

    return result;
  }

  private void getSuperTypeDefinition(Set<ApplicationProfileDef.TypeDef> result, ApplicationProfileDef.TypeDef type) {
    if (result.contains(type)) return;

    result.add(type);

    type.getRule(SubClassOf.class).forEach(subClassOf -> {
      subClassOf.getValue().forEach(parentClassId -> {
        ApplicationProfileDef.TypeDef parentType = type.getApplicationProfileDef().getTypeDef(parentClassId);
        getSuperTypeDefinition(result, parentType);
      });
    });
  }

}
