package zone.cogni.asquare.cube.convertor.json;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.InputStreamSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
      return new ObjectMapper().readValue(input.getInputStream(), CompactConversionProfile.class);
    }
    catch (IOException e) {
      throw new RuntimeException("failed to read resource " + input, e);
    }
  }

  private Map<String, String> prefixes;
  private List<Type> types = new ArrayList<>();

  public Map<String, String> getPrefixes() {
    return prefixes;
  }

  public void setPrefixes(Map<String, String> prefixes) {
    this.prefixes = prefixes;
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

  public static class Type {

    private Set<String> superClasses = new HashSet<>();

    /**
     * for quick lookup based on classId
     */
    private String id;

    /**
     * for quick lookup based on rootRdfType: either there is one or a set
     */
    private String type;

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

    public Set<String> getRealSuperClasses() {
      return getSuperClasses().stream()
                              .filter(superId -> !superId.equals(id))
                              .collect(Collectors.toSet());
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
