package zone.cogni.asquare.applicationprofile.model.basic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;
import io.vavr.control.Option;
import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.basic.def.ApplicationProfileDef;
import zone.cogni.asquare.applicationprofile.rules.Extra;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("ClassMayBeInterface")
public abstract class ApplicationProfile {

  public abstract String getUri();

  public abstract boolean hasType(String typeId);

  public abstract Type getType(String typeId);

  public abstract Map<String, Type> getTypes();

  @JsonIgnore
  public abstract Extra getExtra();

  @JsonIgnore
  public abstract ApplicationProfileDef getApplicationProfileDef();

  @JsonIgnore
  public abstract Set<Class<?>> getInternalClassChain();

  @Override
  public int hashCode() {
    return Objects.hash(getApplicationProfileDef().hashCode());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ApplicationProfile)) return false;

    return getApplicationProfileDef().equals(((ApplicationProfile) o).getApplicationProfileDef());
  }

  // todo Check MultiAttribute logic -> Also use MultiAttribute on SingleType
  // todo Integrate Natan's CollapseApplicationProfile logic in MultiAttribute logic etc...
  // todo Check equals on Attribute class
  @SuppressWarnings("PublicInnerClass")
  public abstract static class Type {

    public static boolean isSuperTypeOf(Type thisType, Type otherType) {
      return otherType.conformsTo(thisType);
    }

    public static boolean isSubTypeOf(Type thisType, Type otherType) {
      return thisType.conformsTo(otherType);
    }

    public static Type calculateType(List<ApplicationProfile.Type> types) {
      if (types.isEmpty()) return null;

      List<BasicType> leafTypes = getDistinctLeafTypes(types);
      Preconditions.checkState(!leafTypes.isEmpty());

      if (leafTypes.size() == 1) return leafTypes.get(0);

      return MultiType.newInstance(leafTypes);
    }

    private static List<BasicType> getDistinctLeafTypes(List<ApplicationProfile.Type> types) {
      Set<BasicType> singleTypes = decompose(types);

      return singleTypes.stream().filter(thisType -> singleTypes.stream()
              .noneMatch(otherType -> thisType != otherType && otherType.conformsTo(thisType))).distinct().collect(Collectors.toList());
    }

    private static Set<BasicType> decompose(List<Type> types) {
      return types.stream()
              .flatMap(type -> type.getTypeDef().stream())
              .map(typeDef -> new BasicType(typeDef))
              .collect(Collectors.toSet());
    }

    @JsonIgnore
    public String getDescription() {
      return "(" + getClass().getSimpleName() + ") "
             + getClassIds().stream().collect(Collectors.joining(", "));
    }

    @JsonIgnore
    public String getClassId() {
      Set<String> values = getClassIds();
      Preconditions.checkState(values.size() == 1, "Type composed from more than one class: " + String.join(",", values));

      return values.stream().findFirst().get();
    }

    @JsonIgnore
    public Optional<String> getClassIdIfSingleType() {
      Set<String> values = getClassIds();
      if (values.size() > 1) Optional.empty();

      return values.stream().findFirst();
    }

    @JsonIgnore
    public abstract ApplicationProfile getApplicationProfile();

    public abstract Set<String> getClassIds();

    @JsonIgnore
    public abstract Set<String> getSuperClassIds();

    public abstract boolean hasAttribute(String attributeId);

    public abstract Attribute getAttribute(String attributeId);

    public abstract Option<Attribute> getAttributeByUri(String uri);

    public abstract Map<String, Attribute> getAttributes();

    @JsonIgnore
    public Collection<String> getAttributeIds() {
      return getAttributes().keySet();
    }

    public abstract <T extends Rule> Option<T> getRule(Class<T> type);

    public abstract <T extends Rule> List<T> getRules(Class<T> type);

    public abstract List<Rule> getRules();

    @JsonIgnore
    public abstract Extra getExtra();

    @JsonIgnore
    public abstract Set<ApplicationProfileDef.TypeDef> getTypeDef();

    /**
     * Must return true if this type conforms (complies with) the type definition of the other type
     * <p>
     * Conforms to/complies with type definition of other type
     * Is subclass of
     */
    public abstract boolean conformsTo(ApplicationProfile.Type otherType);

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Type)) return false;

      return hasSameTypeDefinitions(getTypeDef(), ((Type) o).getTypeDef());
    }

    @Override
    public int hashCode() {
      return Objects.hash(getClassIds().toArray());
    }

    private boolean hasSameTypeDefinitions(Set<ApplicationProfileDef.TypeDef> one, Set<ApplicationProfileDef.TypeDef> two) {
      if (one.size() != two.size()) return false;
      return two.stream().allMatch(element -> hasTypeDefinition(one, element));
    }

    private boolean hasTypeDefinition(Collection<ApplicationProfileDef.TypeDef> collection, ApplicationProfileDef.TypeDef reference) {
      return collection.stream().anyMatch(element -> element.equals(reference));
    }


  }

  @SuppressWarnings("PublicInnerClass")
  public abstract static class Attribute {

    public abstract String getUri();

    @JsonIgnore
    public abstract Type getType();

    public abstract String getAttributeId();

    public abstract <T extends Rule> Option<T> getRule(Class<T> type);

    public abstract <T extends Rule> List<T> getRules(Class<T> type);

    public abstract List<Rule> getRules();

    @JsonIgnore
    public abstract Extra getExtra();

    @JsonIgnore
    public abstract Set<ApplicationProfileDef.AttributeDef> getAttributeDef();

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Attribute)) return false;

      return hasSameAttributeDefinitions(getAttributeDef(), ((Attribute) o).getAttributeDef());
    }

    @Override
    public int hashCode() {
      return Objects.hash(getAttributeId());
    }

    private boolean hasSameAttributeDefinitions(Set<ApplicationProfileDef.AttributeDef> one, Set<ApplicationProfileDef.AttributeDef> two) {
      if (one.size() != two.size()) return false;
      return two.stream().allMatch(element -> hasAttributeDefinition(one, element));
    }

    private boolean hasAttributeDefinition(Collection<ApplicationProfileDef.AttributeDef> collection, ApplicationProfileDef.AttributeDef reference) {
      return collection.stream().anyMatch(element -> element.equals(reference));
    }
  }
}
