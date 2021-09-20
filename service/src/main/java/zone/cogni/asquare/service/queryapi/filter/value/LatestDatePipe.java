package zone.cogni.asquare.service.queryapi.filter.value;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.joda.time.DateTime;
import zone.cogni.asquare.access.ApplicationView.AttributeMatcher;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile.Attribute;
import zone.cogni.asquare.rdf.RdfValue;
import zone.cogni.asquare.rdf.TypedResource;
import zone.cogni.asquare.service.queryapi.filter.AbstractDepthFilterPipe;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

public class LatestDatePipe extends AbstractDepthFilterPipe implements ValueFilterPipe {

  private final String attributeId;
  private final String parentAttributeId;

  @JsonCreator
  public LatestDatePipe(
      @JsonProperty(value = "startDepth") Integer startDepth,
      @JsonProperty(value = "endDepth") Integer endDepth,
      @JsonProperty(value = "attributeId", required = true) String attributeId,
      @JsonProperty(value = "parentAttributeId") String parentAttributeId) {
    super(startDepth, endDepth);
    this.attributeId = attributeId;
    this.parentAttributeId = parentAttributeId;
  }

  public LatestDatePipe(String attributeId, @Nullable String parentAttributeId) {
    this.attributeId = attributeId;
    this.parentAttributeId = parentAttributeId;
  }


  @Override
  public <T extends RdfValue> Stream<T> filterStream(Attribute attribute, Stream<T> values) {
    if (parentAttributeId != null && attribute == null) return values;
    if (parentAttributeId != null && !attribute.getAttributeId().equals(parentAttributeId)) return values;

    return values
//        .filter(v -> ((TypedResource) v).getValue(attributeId) != null)
        .max(compareDates())
        .map(Stream::of)
        .orElseGet(Stream::empty);
  }

  private <T extends RdfValue> Comparator<T> compareDates() {
    return Comparator.comparing(v -> getDateValue((TypedResource) v, attributeId));
  }

  private DateTime getDateValue(TypedResource typedResource, String attribute) {
    return Optional.ofNullable(typedResource.getValue(attribute))
        .map(val -> ((RdfValue) val).getLiteral().getString())
        .map(DateTime::parse)
        .orElse(new DateTime(Long.MIN_VALUE));
  }

  @Override
  public Optional<AttributeMatcher> asAttributeMatcher() {
    return Optional.empty();
  }

  public String getAttributeId() {
    return attributeId;
  }

  public String getParentAttributeId() {
    return parentAttributeId;
  }
}
