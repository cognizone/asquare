package zone.cogni.asquare.applicationprofile.rules;

import zone.cogni.asquare.applicationprofile.model.SingleValueRule;

public class EmbeddedClassId extends SingleValueRule<String> {

  public EmbeddedClassId() {
  }

  public EmbeddedClassId(String value) {
    super(value);
  }

  @Override
  public Class<String> allowedType() {
    return String.class;
  }

  @Override
  public EmbeddedClassId copy() {
    return new EmbeddedClassId(getValueCopy());
  }
}
