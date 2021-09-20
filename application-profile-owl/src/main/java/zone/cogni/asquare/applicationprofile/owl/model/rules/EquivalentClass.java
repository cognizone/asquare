package zone.cogni.asquare.applicationprofile.owl.model.rules;

import zone.cogni.asquare.applicationprofile.model.ListSingleValueRule;

import java.util.List;

public class EquivalentClass extends ListSingleValueRule<String> {

  public EquivalentClass() {
  }

  public EquivalentClass(List<String> value) {
    super(value);
  }

  @Override
  public Class<String> allowedType() {
    return String.class;
  }

  @Override
  public EquivalentClass copy() {
    return new EquivalentClass(getValueCopy());
  }

}
