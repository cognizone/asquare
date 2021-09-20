package zone.cogni.asquare.applicationprofile.rules;

import zone.cogni.asquare.applicationprofile.model.SingleValueRule;

public class InScheme extends SingleValueRule<String> {

  public InScheme() {
  }

  public InScheme(String value) {
    super(value);
  }

  @Override
  public Class<String> allowedType() {
    return String.class;
  }

  @Override
  public InScheme copy() {
    return new InScheme(getValueCopy());
  }
}
