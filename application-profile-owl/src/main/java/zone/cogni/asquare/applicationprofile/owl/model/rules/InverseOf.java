package zone.cogni.asquare.applicationprofile.owl.model.rules;

import zone.cogni.asquare.applicationprofile.model.SingleValueRule;

public class InverseOf extends SingleValueRule<String> {

  public InverseOf() {
  }

  public InverseOf(String value) {
    super(value);
  }

  @Override
  public Class<String> allowedType() {
    return String.class;
  }

  @Override
  public InverseOf copy() {
    return new InverseOf(getValueCopy());
  }
}
