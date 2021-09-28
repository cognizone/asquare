package zone.cogni.asquare.applicationprofile.rules;

import zone.cogni.asquare.applicationprofile.model.SingleValueRule;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RdfType extends SingleValueRule<String> {

  public static RdfTypes toRdfTypes(RdfType rdfType) {
    return new RdfTypes(Stream.of(rdfType.getValue())
                              .collect(Collectors.toList()));
  }

  public RdfType() {
  }

  public RdfType(String value) {
    super(value);
  }

  @Override
  public Class<String> allowedType() {
    return String.class;
  }

  @Override
  public RdfType copy() {
    return new RdfType(getValueCopy());
  }
}
