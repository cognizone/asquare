package zone.cogni.asquare.applicationprofile.rules;

import zone.cogni.asquare.applicationprofile.model.SingleValueRule;

public class MinCardinality extends SingleValueRule<Integer> {

  public MinCardinality() {
  }

  public MinCardinality(Integer value) {
    super(value);
  }

  @Override
  public Class<Integer> allowedType() {
    return Integer.class;
  }

  @Override
  public MinCardinality copy() {
    return new MinCardinality(getValueCopy());
  }
}
