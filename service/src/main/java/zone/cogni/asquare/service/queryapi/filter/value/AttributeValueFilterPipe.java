package zone.cogni.asquare.service.queryapi.filter.value;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import zone.cogni.asquare.access.ApplicationView;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile.Attribute;
import zone.cogni.asquare.rdf.RdfValue;
import zone.cogni.asquare.service.queryapi.filter.AbstractDepthFilterPipe;

import java.util.Optional;
import java.util.stream.Stream;

public class AttributeValueFilterPipe extends AbstractDepthFilterPipe implements ValueFilterPipe {

  private final String attributeId;
  private final String value;

  @JsonCreator
  public AttributeValueFilterPipe(
    @JsonProperty(value = "startDepth") Integer startDepth,
    @JsonProperty(value = "endDepth") Integer endDepth,
    @JsonProperty(value = "attributeId", required = true) String attributeId,
    @JsonProperty(value = "value", required = true) String value) {
    super(startDepth, endDepth);
    this.attributeId = attributeId;
    this.value = value;
  }

  public AttributeValueFilterPipe(String attributeId, String value) {
    super(0, 1);
    this.attributeId = attributeId;
    this.value = value;
  }

  @Override
  public <T extends RdfValue> Stream<T> filterStream(Attribute attribute, Stream<T> values) {
    if (attribute == null) return values;

    if (!attribute.getAttributeId().equals(attributeId)) return values;
    return values.filter(this::isMatch);
  }

  private boolean isMatch(RdfValue value) {
    String asString = value.isResource() ? value.getResource().getURI() : value.getLiteral().getString();
    return asString.equals(this.value);
  }

  @Override
  public Optional<ApplicationView.AttributeMatcher> asAttributeMatcher() {
    return Optional.empty();
  }

  public String getAttributeId() {
    return attributeId;
  }

  public String getValue() {
    return value;
  }
}
