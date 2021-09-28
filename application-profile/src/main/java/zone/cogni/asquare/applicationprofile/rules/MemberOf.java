package zone.cogni.asquare.applicationprofile.rules;

import zone.cogni.asquare.applicationprofile.model.SingleValueRule;

public class MemberOf extends SingleValueRule<String> {

  public MemberOf() {
  }

  public MemberOf(String value) {
    super(value);
  }

  @Override
  public Class<String> allowedType() {
    return String.class;
  }

  @Override
  public MemberOf copy() {
    return new MemberOf(getValueCopy());
  }
}
