package zone.cogni.asquare.applicationprofile.rules;

import zone.cogni.asquare.applicationprofile.model.ListSingleValueRule;

import java.util.List;

public class RdfTypes extends ListSingleValueRule<String> {

  public RdfTypes() {
  }

  public RdfTypes(List<String> value) {
    super(value);
  }

  @Override
  public Class<String> allowedType() {
    return String.class;
  }

  @Override
  public RdfTypes copy() {
    return new RdfTypes(getValueCopy());
  }
}
