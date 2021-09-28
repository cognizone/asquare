package zone.cogni.asquare.service.queryapi.filter.attribute;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Sets;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile.Attribute;
import zone.cogni.asquare.service.queryapi.filter.AbstractDepthFilterPipe;

import java.util.Collections;
import java.util.Set;
import java.util.function.Predicate;

public class IncludeTypePipe extends AbstractDepthFilterPipe implements AttributeFilterPipe {

  private final Predicate<Attribute> predicate;
  private final Set<String> included;

  @JsonCreator
  public IncludeTypePipe(@JsonProperty(value = "startDepth") Integer startDepth,
                         @JsonProperty(value = "endDepth") Integer endDepth,
                         @JsonProperty(value = "included") Set<String> included) {
    super(startDepth, endDepth);

    Set<String> finalIncluded = included == null ? Collections.emptySet() : included;
    this.predicate = (a) -> !Sets.intersection(a.getType().getClassIds(), finalIncluded).isEmpty();
    this.included = included;
  }


  public IncludeTypePipe(int startDepth, Set<String> included) {
    super(startDepth);
    this.predicate = (a) ->!Sets.intersection(a.getType().getClassIds(), included).isEmpty();
    this.included = included;
  }

  public IncludeTypePipe(int startDepth) {
    super(startDepth);
    this.predicate = (a) -> false;
    this.included = Collections.emptySet();
  }

  @Override
  @JsonIgnore
  public Predicate<Attribute> getFilter() {
    return predicate;
  }
}
