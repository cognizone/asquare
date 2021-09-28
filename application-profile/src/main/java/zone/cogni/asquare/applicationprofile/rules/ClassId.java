package zone.cogni.asquare.applicationprofile.rules;

import zone.cogni.asquare.applicationprofile.model.SingleValueRule;

public class ClassId extends SingleValueRule<String> implements Comparable<ClassId> {

  public ClassId() {
  }

  public ClassId(String value) {
    super(value);
  }

  @Override
  public Class<String> allowedType() {
    return String.class;
  }

  @Override
  public ClassId copy() {
    return new ClassId(getValueCopy());
  }

  @Override
  public int compareTo(ClassId other) {
    return this.getValue().compareTo(other.getValue());
  }
}
