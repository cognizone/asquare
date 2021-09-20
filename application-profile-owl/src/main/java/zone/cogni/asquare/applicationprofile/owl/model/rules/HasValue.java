package zone.cogni.asquare.applicationprofile.owl.model.rules;

import zone.cogni.asquare.applicationprofile.model.SingleValueRule;

// TODO support Literal and Resource !!
public class HasValue extends SingleValueRule<String> {

  public HasValue() {
  }

  public HasValue(String value) {
    super(value);
  }

  @Override
  public Class<String> allowedType() {
    return String.class;
  }

  @Override
  public HasValue copy() {
    return new HasValue(getValueCopy());
  }

}
