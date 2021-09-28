package zone.cogni.asquare.applicationprofile.owl.model.rules;

import zone.cogni.asquare.applicationprofile.model.ListSingleValueRule;

import java.util.List;

public class EquivalentProperty extends ListSingleValueRule<String> {

  public EquivalentProperty() {
  }

  public EquivalentProperty(List<String> value) {
    super(value);
  }

  @Override
  public Class<String> allowedType() {
    return String.class;
  }

  @Override
  public EquivalentProperty copy() {
    return new EquivalentProperty(getValueCopy());
  }

}
