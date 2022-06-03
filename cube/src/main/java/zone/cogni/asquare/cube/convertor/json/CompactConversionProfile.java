package zone.cogni.asquare.cube.convertor.json;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * <p>
 * CompactConversionProfile is mainly for reading JSON files containing a ConversionProfile.
 * Properties of superclasses are not repeated here.
 * </p>
 * <p>
 * In case we want to work with conversions, we are converting this using
 * {@link CompactConversionProfileToConversionProfile} in a {@link ConversionProfile}.
 * </p>
 */
public class CompactConversionProfile {

  public static CompactConversionProfile read(InputStreamSource input) {
    try {
      ObjectMapper objectMapper = new ObjectMapper();
      objectMapper.enable(JsonParser.Feature.ALLOW_COMMENTS);
      return objectMapper.readValue(input.getInputStream(), CompactConversionProfile.class);
    }
    catch (IOException e) {
      throw new RuntimeException("failed to read resource " + input, e);
    }
  }

  private Map<String, String> prefixes;
  private List<String> imports;
  private List<Type> types = new ArrayList<>();

  public Map<String, String> getPrefixes() {
    return prefixes;
  }

  public void setPrefixes(Map<String, String> prefixes) {
    this.prefixes = new TreeMap<>(prefixes);
  }

  void addPrefix(String prefix, String uri) {
    if (prefixes == null)
      prefixes = new TreeMap<>();

    if (!prefixes.containsKey(prefix)) {
      prefixes.put(prefix, uri);
      return;
    }

    // check if we have a match if it already exists
    String existingUri = prefixes.get(prefix);
    if (!existingUri.equals(uri)) {
      throw new RuntimeException("prefix '" + prefix + "' has values '" + uri + "' and '" + existingUri + "'");
    }
  }

  public List<String> getImports() {
    return imports;
  }

  public void setImports(List<String> imports) {
    this.imports = imports;
  }

  public List<Type> getTypes() {
    return types;
  }

  public Type getById(String id) {
    return types.stream()
                .filter(type -> type.getId().equals(id))
                .findFirst()
                .orElse(null);
  }

  public void setTypes(List<Type> types) {
    types.forEach(type -> type.setConversionProfile(this));
    this.types = types;
  }

  void addType(Type newType) {
    if (types == null) types = new ArrayList<>();

    if (isTypePresent(newType)) {
      Type currentType = getById(newType.getId());

      if (isMergeAllowed(newType, currentType)) {
        newType.getAttributes()
               .forEach(currentType::addAttribute);
      }
      else {
        throw new RuntimeException("type '" + newType.getId() + "' already defined with different fields");
      }
    }
    else {
      newType.setConversionProfile(this);
      types.add(newType);
    }
  }


  private boolean isMergeAllowed(Type newType, Type currentType) {
    boolean newTypeIsEmpty = newType.getSuperClasses().isEmpty() && newType.getType() == null;
    boolean typesHaveSameFields = newType.getSuperClasses().equals(currentType.getSuperClasses())
                                  && newType.getType().equals(currentType.getType());
    return newTypeIsEmpty || typesHaveSameFields;
  }

  private boolean isTypePresent(Type type) {
    return getById(type.getId()) != null;
  }

  public static class Type {

    private static final Logger log = LoggerFactory.getLogger(Type.class);


    /**
     * for quick lookup based on classId
     */
    private String id;

    /**
     * for quick lookup based on rootRdfType: either there is one or a set
     */
    private String type;
    private Set<String> superClasses = new HashSet<>();

    private List<Attribute> attributes = new ArrayList<>();

    @JsonIgnore
    private CompactConversionProfile conversionProfile;

    public Set<String> getSuperClasses() {
      return superClasses;
    }

    public void setSuperClasses(Set<String> superClasses) {
      this.superClasses = superClasses;
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public List<Attribute> getAttributes() {
      return attributes;
    }

    public void setAttributes(List<Attribute> attributes) {
      attributes.forEach(attribute -> attribute.setParentType(this));
      this.attributes = attributes;
    }

    public CompactConversionProfile getConversionProfile() {
      return conversionProfile;
    }

    public void setConversionProfile(CompactConversionProfile conversionProfile) {
      this.conversionProfile = conversionProfile;
    }

    public Attribute getById(String id) {
      return attributes.stream()
                       .filter(attribute -> attribute.getId().equals(id))
                       .findFirst()
                       .orElse(null);
    }

    @JsonIgnore
    public Set<String> getRealSuperClasses() {
      return getSuperClasses().stream()
                              .filter(superId -> !superId.equals(id))
                              .collect(Collectors.toSet());
    }

    private void addAttribute(Attribute newAttribute) {
      if (attributes == null) attributes = new ArrayList<>();

      if (isAttributePresent(newAttribute)) {
        Attribute currentAttribute = getById(newAttribute.getId());

        mergePropertyField(newAttribute, currentAttribute);
        mergeInverseField(newAttribute, currentAttribute);
        mergeSingleField(newAttribute, currentAttribute);
        mergeTypeField(newAttribute, currentAttribute);
      }
      else {
        attributes.add(newAttribute);
        newAttribute.setParentType(this);
      }
    }

    private void mergePropertyField(Attribute newAttribute, Attribute currentAttribute) {
      if (!newAttribute.getProperty().equals(currentAttribute.getProperty())) {
        throw new RuntimeException("attribute '" + getFullAttributeName(currentAttribute) + "' has different properties " +
                                   "'" + newAttribute.getProperty() + "' and '" + currentAttribute.getProperty() + "'.");
      }
    }

    private void mergeInverseField(Attribute newAttribute, Attribute currentAttribute) {
      if (newAttribute.isInverse() != currentAttribute.isInverse()) {
        throw new RuntimeException("attribute '" + getFullAttributeName(currentAttribute) + "' has different inverses.");
      }
    }

    private void mergeSingleField(Attribute newAttribute, Attribute currentAttribute) {
      if (currentAttribute.isSingle() && !newAttribute.isSingle()) {
        // less strict, add warning
        log.warn("attribute '{}' was single and now not?", getFullAttributeName(currentAttribute));
      }
      else if (!currentAttribute.isSingle() && newAttribute.isSingle()) {
        // make it more strict
        currentAttribute.setSingle(true);
      }
    }

    private void mergeTypeField(Attribute newAttribute, Attribute currentAttribute) {
      if (currentAttribute.getType() == Attribute.Type.mix && newAttribute.getType() != Attribute.Type.mix) {
        // make it more strict
        currentAttribute.setType(newAttribute.getType());
      }
      else if (currentAttribute.getType() != Attribute.Type.mix && newAttribute.getType() == Attribute.Type.mix) {
        // less strict, add a warning
        log.warn("attribute '{}' type was '{}' and now 'mix'?",
                 getFullAttributeName(currentAttribute), currentAttribute.getType());
      }
      else if (currentAttribute.getType() != newAttribute.getType()) {
        throw new RuntimeException("attribute '" + getFullAttributeName(currentAttribute) + "' has non-overlapping types: "
                                   + "'" + currentAttribute.getType() + "' and '" + newAttribute.getType() + "'.");
      }
    }

    private String getFullAttributeName(Attribute attribute) {
      return attribute.getParentType().getId() + "." + attribute.getId();
    }

    private boolean isAttributePresent(Attribute attribute) {
      return getById(attribute.getId()) != null;
    }
  }

  public static class Attribute {

    public enum Type {
      object,
      datatype,
      mix;

      public ConversionProfile.Attribute.Type toExpandedAttributeType() {
        if (this == Type.mix) return ConversionProfile.Attribute.Type.mix;
        if (this == Type.datatype) return ConversionProfile.Attribute.Type.attribute;
        if (this == Type.object) return ConversionProfile.Attribute.Type.reference;

        throw new RuntimeException("Could not match " + this);
      }
    }

    private String id;
    private String property;
    private boolean inverse;

    private boolean single;
    private Type type = Type.mix;

    @JsonIgnore
    private CompactConversionProfile.Type parentType;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getProperty() {
      return property;
    }

    public void setProperty(String property) {
      this.property = property;
    }

    public boolean isInverse() {
      return inverse;
    }

    public void setInverse(boolean inverse) {
      this.inverse = inverse;
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

    public CompactConversionProfile.Type getParentType() {
      return parentType;
    }

    public void setParentType(CompactConversionProfile.Type parentType) {
      this.parentType = parentType;
    }

    /**
     * @return true if fields {@code id}, {@code property}, {@code inverse}, {@code single} and {@code type} are equal.
     */
    public boolean hasSimilarMetadata(Attribute other) {
      return id.equals(other.id)
             && property.equals(other.property)
             && inverse == other.inverse
             && single == other.single
             && type == other.type;
    }

  }

}
