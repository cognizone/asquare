package zone.cogni.asquare.service.queryapi.filter.value;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import zone.cogni.asquare.access.ApplicationView;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile.Attribute;
import zone.cogni.asquare.applicationprofile.rules.Datatype;
import zone.cogni.asquare.applicationprofile.rules.Range;
import zone.cogni.asquare.rdf.RdfValue;
import zone.cogni.asquare.rdf.TypedResource;
import zone.cogni.asquare.service.queryapi.filter.AbstractDepthFilterPipe;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AttributeMatcherPipe extends AbstractDepthFilterPipe implements ValueFilterPipe {

  private final String parentAttributeId;
  private final String attributeId;
  private final String value;

  private final TypeMapper typeMapper = TypeMapper.getInstance();

  @JsonCreator
  public AttributeMatcherPipe(
      @JsonProperty(value = "startDepth") Integer startDepth,
      @JsonProperty(value = "endDepth") Integer endDepth,
      @JsonProperty(value = "parentAttributeId") String parentAttributeId,
      @JsonProperty(value = "attributeId", required = true) String attributeId,
      @JsonProperty(value = "value", required = true) String value) {
    super(startDepth, endDepth);
    this.parentAttributeId = parentAttributeId;
    this.attributeId = attributeId;
    this.value = value;
  }

  public AttributeMatcherPipe(String attributeId, String value, @Nullable String parentAttributeId) {
    this.parentAttributeId = parentAttributeId;
    this.attributeId = attributeId;
    this.value = value;
  }

  public AttributeMatcherPipe(String attributeId, String value) {
    super(0, 1);
    this.parentAttributeId = null;
    this.attributeId = attributeId;
    this.value = value;
  }

  @Override
  public <T extends RdfValue> Stream<T> filterStream(Attribute attribute, Stream<T> values) {
    if (attribute == null || !attribute.getAttributeId().equals(parentAttributeId)) return values;

    return values
        .filter(resource -> valueMatching(((TypedResource) resource).getValues(attributeId)));
  }

  private boolean valueMatching(List<RdfValue> values) {
    Set<String> asString = values.stream()
        .map(v -> v.isResource() ? v.getResource().getURI() : v.getLiteral().getString())
        .collect(Collectors.toSet()) ;
    return asString.contains(value);
  }


  @Override
  public Optional<ApplicationView.AttributeMatcher> asAttributeMatcher() {
    return parentAttributeId == null
        ? Optional.of(ApplicationView.AttributeMatcher.match(attributeId, a -> getValue(a, value)))
        : Optional.empty();
  }

  private RDFNode getValue(Attribute attribute, String value) {
   return
       attribute.getRule(Range.class)
           .map(rule -> rule.getValue())
           .filter(rule -> rule instanceof Datatype)
        .map(rule -> ((Datatype)rule).getValue())
       .map(dt -> (RDFNode) ResourceFactory.createTypedLiteral(value, typeMapper.getTypeByName(dt)))
       .getOrElse(ResourceFactory.createResource(value));
  }

  public String getAttributeId() {
    return attributeId;
  }

  public String getValue() {
    return value;
  }

  public String getParentAttributeId() {
    return parentAttributeId;
  }
}
