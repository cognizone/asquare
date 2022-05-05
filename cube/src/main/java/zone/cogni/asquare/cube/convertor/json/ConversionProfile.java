package zone.cogni.asquare.cube.convertor.json;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;
import org.springframework.core.io.InputStreamSource;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/*
 TODO  ---------------------------------------------------------------
 TODO  ------------------- some important notes !! -------------------
 TODO  ---------------------------------------------------------------

 - no min / max cardinality
      - currently only single boolean
      - currently no required boolean
      - min / max or required / single ?

 - no range
      - no datatype
      - no class id
      - could be added
          - if so, think about multiple allowed types !

 - all class ids !
      - not in collapsed application profile !!

 - root rdf type
      - not in collapsed application profile !!

 - can do inverse
      - not possible in application profile!

 TODO  ---------------------------------------------------------------
 */


public class ConversionProfile {

  public static ConversionProfile read(InputStreamSource resource) {
    return new CompactConversionProfileToConversionProfile().apply(CompactConversionProfile.read(resource));
  }

  private Map<String, String> prefixes;
  private List<Type> types = new ArrayList<>();

  /**
   * simple classId lookup
   */
  @JsonIgnore
  private final Map<String, Type> classIdTypeMap = new HashMap<>();

  /**
   * expanded classId lookup
   */
  @JsonIgnore
  private final Map<String, Type> expandedClassIdTypeMap = new HashMap<>();


  /**
   * Used for lookup of most specific Type from a list of classIds,
   * typically from lowest level (first collection) but possible from other levels.
   * <p>
   * Note: it is possible, in theory, that this lookup fails
   * if there are multiple classIds at the same level.
   */
  @JsonIgnore
  private final List<Collection<Type>> typesByLevel = new ArrayList<>();

  @JsonIgnore
  private final Map<String, Type> rdfTypeTypeMap = new HashMap<>();

  @JsonIgnore
  private final Map<String, Type> expandedRdfTypeTypeMap = new HashMap<>();

  public Map<String, String> getPrefixes() {
    return prefixes;
  }

  public void setPrefixes(Map<String, String> prefixes) {
    this.prefixes = prefixes;
  }

  public List<Type> getTypes() {
    return types;
  }

  public void setTypes(List<Type> types) {
    this.types = types;
  }

  @JsonIgnore
  public Set<String> getClassIds() {
    return classIdTypeMap.keySet();
  }

  @JsonIgnore
  public Set<String> getRdfTypes() {
    return rdfTypeTypeMap.keySet();
  }

  public Type getTypeFromRdfType(String rdfType) {
    return rdfTypeTypeMap.get(rdfType);
  }

  public Type getTypeFromExpandedRdfType(String rdfType) {
    return expandedRdfTypeTypeMap.get(rdfType);
  }

  public Type getTypeFromClassId(String classId) {
    return classIdTypeMap.get(classId);
  }

  public Type getLeafType(Collection<String> classIds) {
    for (Collection<Type> types : typesByLevel) {
      Set<Type> candidates = types.stream()
                                  .filter(type -> classIds.contains(type.rootClassId))
                                  .collect(Collectors.toSet());

      if (candidates.size() == 1) return candidates.stream().findFirst().get();
      if (candidates.size() >= 1) throw new RuntimeException("Multiple matches found.");
    }

    return null;
  }

  public Type getTypeFromClassIds(Collection<String> input) {
    List<Type> result = this.classIdTypeMap.values()
                                           .stream()
                                           .filter(t -> CollectionUtils.isEqualCollection(t.classIds, input))
                                           .collect(Collectors.toList());
    if (result.size() != 1) {
      String types = result.stream().map(Type::getRootClassId).collect(Collectors.joining());
      throw new RuntimeException("type lookup failed: input " + input + " and result " + types);
    }
    return result.get(0);
  }

  public Type getTypeFromRdfTypes(Collection<String> input) {
    List<Type> result = this.expandedClassIdTypeMap.values()
                                                   .stream()
                                                   .filter(t -> CollectionUtils.isEqualCollection(t.expandedRdfTypes, input))
                                                   .collect(Collectors.toList());
    if (result.size() != 1) {
      List<String> types = result.stream().map(Type::getRootClassId).collect(Collectors.toList());
      throw new RuntimeException("expecting exactly one type for input " + input + ", found " + types);
    }

    return result.get(0);
  }

  public Type getBestMatchingTypeFromRdfTypes(Collection<String> input) {
    List<Type> result = this.classIdTypeMap.values()
                                           .stream()
                                           .filter(t -> CollectionUtils.containsAll(input, t.rdfTypes))
                                           .sorted(Comparator.comparing(t -> CollectionUtils.intersection(((ConversionProfile.Type) t).rdfTypes, input)
                                                                                            .size()).reversed())
                                           .collect(Collectors.toList());
    if (result.isEmpty()) {
      throw new RuntimeException("no class matches input " + input);
    }
    if (result.size() >= 2 && result.get(0).getRdfTypes().size() == result.get(1).getRdfTypes().size()) {
      throw new RuntimeException("there is a tie in the first result classes, can't pick one between: " +
                                 result.get(0).getRdfTypes() + " and " + result.get(1).getRdfTypes());
    }

    return result.get(0);
  }

  public void add(Type type) {
    Preconditions.checkNotNull(type);
    Preconditions.checkNotNull(type.rootClassId);
    Preconditions.checkNotNull(type.rootRdfType);

    types.add(type);
    classIdTypeMap.put(type.rootClassId, type);
    expandedClassIdTypeMap.put(curieToFullUri(type.rootClassId), type);
    rdfTypeTypeMap.put(type.rootRdfType, type);
    expandedRdfTypeTypeMap.put(curieToFullUri(type.rootRdfType), type);
  }

  private String curieToFullUri(String curieOrUri) {
    if (!curieOrUri.contains(":")) return curieOrUri;

    String prefix = StringUtils.substringBefore(curieOrUri, ":");
    if (prefixes == null || !prefixes.containsKey(prefix)) return curieOrUri;

    return prefixes.get(prefix) + StringUtils.substringAfter(curieOrUri, ":");
  }

  public void done() {
    calculateTypesByLevel();
  }

  private void calculateTypesByLevel() {
    Set<Type> types = new HashSet<>(classIdTypeMap.values());
    while (!types.isEmpty()) {
      Set<Type> levelTypes = calculateTypesByLevel(types);

      typesByLevel.add(levelTypes);
      types.removeAll(levelTypes);
    }
  }

  private Set<Type> calculateTypesByLevel(Collection<Type> types) {
    return types.stream()
                .filter(type -> isLeafType(types, type))
                .collect(Collectors.toSet());
  }

  private boolean isLeafType(Collection<Type> types, Type type) {
    return types.stream()
                .filter(current -> !current.rootClassId.equals(type.rootClassId))
                .noneMatch(current -> current.classIds.contains(type.rootClassId));
  }

  public String toString() {
    StringBuilder result = new StringBuilder();
    result.append("Application Profile:");
    classIdTypeMap.values()
                  .forEach(type -> result.append("\n").append(type));
    return result.toString();
  }

  public static class Type {
    /**
     * for quick lookup based on classId
     */
    private String rootClassId;

    /**
     * for quick lookup based on rootRdfType: either there is one or a set
     */
    private String rootRdfType;

    @JsonIgnore
    private String expandedRootRdfType;

    /**
     * all classIds of type
     */
    private Collection<String> classIds;

    /**
     * all rdfTypes
     */
    private Collection<String> rdfTypes;

    @JsonIgnore
    private Collection<String> expandedRdfTypes;

    private List<Attribute> attributes = new ArrayList<>();

    /**
     * all attributes
     */
    @JsonIgnore
    private final Map<String, Attribute> attributeIdMap = new HashMap<>();

    @JsonIgnore
    private final Map<String, Attribute> attributeUriMap = new HashMap<>();

    public String getRootClassId() {
      return rootClassId;
    }

    public void setRootClassId(String rootClassId) {
      this.rootClassId = rootClassId;
    }

    public String getRootRdfType() {
      return rootRdfType;
    }

    public void setRootRdfType(String rootRdfType) {
      this.rootRdfType = rootRdfType;
    }

    public String getExpandedRootRdfType() {
      return expandedRootRdfType;
    }

    public void setExpandedRootRdfType(String expandedRootRdfType) {
      this.expandedRootRdfType = expandedRootRdfType;
    }

    public Collection<String> getClassIds() {
      return classIds;
    }

    public void setClassIds(Collection<String> classIds) {
      this.classIds = classIds;
    }

    public Collection<String> getRdfTypes() {
      return rdfTypes;
    }

    public void setRdfTypes(Collection<String> rdfTypes) {
      this.rdfTypes = rdfTypes;
    }

    public Collection<String> getExpandedRdfTypes() {
      return expandedRdfTypes;
    }

    public void setExpandedRdfTypes(Collection<String> expandedRdfTypes) {
      this.expandedRdfTypes = expandedRdfTypes;
    }

    public List<Attribute> getAttributes() {
      return attributes;
    }

    public void setAttributes(List<Attribute> attributes) {
      this.attributes = attributes;
    }

    public void add(@Nonnull Attribute attribute) {
      attributes.add(attribute);

      Attribute oldIdValue = attributeIdMap.put(attribute.attributeId, attribute);
      if (oldIdValue != null) throw new RuntimeException("attribute " + attribute.attributeId + " id already present");

      Attribute oldUriValue = attributeUriMap.put(attribute.uri, attribute);
      if (oldUriValue != null) throw new RuntimeException("attribute " + attribute.uri + " uri already present");
    }

    @JsonIgnore
    public Set<String> getAttributeIds() {
      return attributeIdMap.keySet();
    }

    public Attribute getByAttributeId(String attributeId) {
      return attributeIdMap.get(attributeId);
    }

    public Attribute getByAttributeUri(String attributeUri) {
      return attributeUriMap.get(attributeUri);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Type type = (Type) o;

      return rootClassId.equals(type.rootClassId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(rootClassId);
    }

    public String toString() {
      StringBuilder result = new StringBuilder();
      result.append("\t Type ").append(rootClassId);
      attributeIdMap.values()
                    .forEach(attribute -> result.append("\n").append(attribute));
      return result.toString();
    }
  }

  public static class Attribute {

    public enum Type {
      reference,
      attribute,
      mix;

      public CompactConversionProfile.Attribute.Type toCompactAttributeType() {
        if (this == Type.mix) return CompactConversionProfile.Attribute.Type.mix;
        if (this == Type.attribute) return CompactConversionProfile.Attribute.Type.datatype;
        if (this == Type.reference) return CompactConversionProfile.Attribute.Type.object;

        throw new RuntimeException("Could not match " + this);
      }
    }

    private String attributeId;
    private String uri;

    @JsonIgnore
    private String expandedUri;

    private boolean inverse;

    @JsonIgnore
    private Property property;

    private boolean single;
    private Type type = Type.mix;

    public String getAttributeId() {
      return attributeId;
    }

    public void setAttributeId(String attributeId) {
      this.attributeId = attributeId;
    }

    public String getUri() {
      return uri;
    }

    public void setUri(String uri) {
      this.uri = uri;
    }

    public void setExpandedUri(String expandedUri) {
      this.expandedUri = expandedUri;
      this.property = ResourceFactory.createProperty(expandedUri);
    }


    public boolean isInverse() {
      return inverse;
    }

    public void setInverse(boolean inverse) {
      this.inverse = inverse;
    }

    public Property getProperty() {
      return property;
    }

    public boolean isSingle() {
      return single;
    }

    public void setSingle(boolean single) {
      this.single = single;
    }

    public Type getType() {
      return type;
    }

    public void setType(Type type) {
      this.type = type;
    }

    @JsonIgnore
    public boolean isList() {
      return !isSingle();
    }

    @JsonIgnore
    public boolean isReference() {
      return type == Type.reference;
    }

    @JsonIgnore
    public boolean isAttribute() {
      return type == Type.attribute;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Attribute attribute = (Attribute) o;
      return attributeId.equals(attribute.attributeId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(attributeId);
    }

    public String toString() {
      return "\t\t Attribute"
             + " [" + (single ? "1" : "n") + "] "
             + StringUtils.rightPad(attributeId, 20)
             + " : " + uri;
    }
  }

}
