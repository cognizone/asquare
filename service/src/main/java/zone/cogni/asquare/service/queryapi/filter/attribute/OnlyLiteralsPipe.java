package zone.cogni.asquare.service.queryapi.filter.attribute;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Sets;
import zone.cogni.asquare.applicationprofile.model.SingleValueRule;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile.Attribute;
import zone.cogni.asquare.applicationprofile.rules.Datatype;
import zone.cogni.asquare.applicationprofile.rules.Range;
import zone.cogni.asquare.service.queryapi.filter.AbstractDepthFilterPipe;

import java.util.Set;
import java.util.function.Predicate;

public class OnlyLiteralsPipe extends AbstractDepthFilterPipe implements AttributeFilterPipe {

  private final Predicate<Attribute> predicate;
  private final Set<String> types;

  @JsonCreator
  public OnlyLiteralsPipe(@JsonProperty(value = "startDepth") Integer startDepth,
                     @JsonProperty(value = "endDepth") Integer endDepth,
                     @JsonProperty(value = "types", required = true) Set<String> types) {
    super(startDepth, endDepth);

    this.predicate = (a) -> Sets.intersection(a.getType().getClassIds(), types).isEmpty() || isLiteral(a);
    this.types = types;
  }

  public OnlyLiteralsPipe(int startDepth, Set<String> types) {
    super(startDepth);
    this.predicate = (a) -> Sets.intersection(a.getType().getClassIds(), types).isEmpty() || isLiteral(a);
    this.types = types;
  }

  @Override
  @JsonIgnore
  public Predicate<Attribute> getFilter() {
    return this.predicate;
  }

  private boolean isLiteral(Attribute attribute) {
    return attribute.getRules(Range.class)
        .stream()
        .map(SingleValueRule::getValue)
        .anyMatch(val ->  val instanceof Datatype);
  }

  public Set<String> getTypes() {
    return types;
  }
}
