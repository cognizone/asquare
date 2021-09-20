package zone.cogni.asquare.applicationprofile.owl.model.rules;

import zone.cogni.asquare.applicationprofile.model.ListSingleValueRule;

import java.util.List;

public class SubPropertyOf extends ListSingleValueRule<String> {

  public SubPropertyOf() {
  }

  public SubPropertyOf(List<String> value) {
    super(value);
  }

  @Override
  public Class<String> allowedType() {
    return String.class;
  }

  @Override
  public SubPropertyOf copy() {
    return new SubPropertyOf(getValueCopy());
  }
}
