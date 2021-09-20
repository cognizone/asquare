package zone.cogni.asquare.service.queryapi.filter.value;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import zone.cogni.asquare.access.ApplicationView;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile.Attribute;
import zone.cogni.asquare.edit.ConstructedResource;
import zone.cogni.asquare.rdf.RdfValue;
import zone.cogni.asquare.rdf.TypedResource;
import zone.cogni.asquare.service.queryapi.filter.AbstractDepthFilterPipe;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class IncludeAttributePipe extends AbstractDepthFilterPipe implements ValueFilterPipe {

  private final Set<String> included;

  @JsonCreator
  public IncludeAttributePipe(@JsonProperty(value = "startDepth") Integer startDepth,
                              @JsonProperty(value = "endDepth") Integer endDepth,
                              @JsonProperty(value = "included") Set<String> included) {
    super(startDepth, endDepth);
    this.included = included;
  }


  public IncludeAttributePipe(int startDepth, Set<String> included) {
    super(startDepth);
    this.included = included;
  }

  public IncludeAttributePipe(int startDepth) {
    super(startDepth);
    this.included = Collections.emptySet();
  }

  @Override
  public <T extends RdfValue> Stream<T> filterStream(Attribute attribute, Stream<T> values) {
    if (attribute == null || included.contains(attribute.getAttributeId())) return values;

    return values.map(v -> v instanceof TypedResource
        ? (T) createSimpleReference((TypedResource) v)
        : v);
  }

  private TypedResource createSimpleReference(TypedResource resource) {
    return ConstructedResource.create(resource::getType, () -> resource.getResource().getURI()).get();
  }

  @Override
  public Optional<ApplicationView.AttributeMatcher> asAttributeMatcher() {
    return Optional.empty();
  }

  public Set<String> getIncluded() {
    return included;
  }
}
