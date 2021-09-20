package zone.cogni.asquare.applicationprofile.rules;

import zone.cogni.asquare.applicationprofile.model.SingleValueRule;

public class LiteralValue extends SingleValueRule<String> {

  public LiteralValue() {
  }

  public LiteralValue(String value) {
    super(value);
  }

  @Override
  public Class<String> allowedType() {
    return String.class;
  }

  @Override
  public LiteralValue copy() {
    return new LiteralValue(getValueCopy());
  }
}
