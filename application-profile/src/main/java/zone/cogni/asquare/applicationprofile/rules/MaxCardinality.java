package zone.cogni.asquare.applicationprofile.rules;

import zone.cogni.asquare.applicationprofile.model.SingleValueRule;

public class MaxCardinality extends SingleValueRule<Integer> {

  public MaxCardinality() {
  }

  public MaxCardinality(Integer value) {
    super(value);
  }

  @Override
  public Class<Integer> allowedType() {
    return Integer.class;
  }

  @Override
  public MaxCardinality copy() {
    return new MaxCardinality(getValueCopy());
  }
}
