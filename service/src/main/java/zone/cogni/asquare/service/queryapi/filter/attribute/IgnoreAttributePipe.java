package zone.cogni.asquare.service.queryapi.filter.attribute;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.service.queryapi.filter.AbstractDepthFilterPipe;
import zone.cogni.asquare.web.rest.controller.exceptions.BadInputException;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class IgnoreAttributePipe extends AbstractDepthFilterPipe implements AttributeFilterPipe {

  private final String attribute;
  private final Supplier<RuntimeException> exception;
  private final Function<String, RuntimeException> defaultException =
      attribute -> new BadInputException("Attribute '" + attribute + "' not allowed");

  @JsonCreator
  public IgnoreAttributePipe(
      @JsonProperty(value = "startDepth") Integer startDepth,
      @JsonProperty(value = "endDepth") Integer endDepth,
      @JsonProperty(value = "attribute") String attribute) {
    super(startDepth, endDepth);
    this.attribute = attribute;
    this.exception = () -> defaultException.apply(attribute);
  }

  public IgnoreAttributePipe(String attribute) {
    super();
    this.attribute = attribute;
    this.exception = () -> defaultException.apply(attribute);
  }

  public IgnoreAttributePipe(String attribute, Supplier<RuntimeException> exception) {
    super();
    this.attribute = attribute;
    this.exception = exception;
  }

  public String getAttribute() {
    return attribute;
  }

  @Override
  @JsonIgnore
  public Predicate<ApplicationProfile.Attribute> getFilter() {
    return a -> !a.getAttributeId().equals(attribute);
  }
}
