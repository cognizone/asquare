package zone.cogni.asquare.applicationprofile.rules;

import zone.cogni.asquare.applicationprofile.model.SingleValueRule;

public class MaxLangCardinality extends SingleValueRule<Integer> {

  public MaxLangCardinality() {
  }

  public MaxLangCardinality(Integer value) {
    super(value);
  }

  @Override
  public Class<Integer> allowedType() {
    return Integer.class;
  }

  @Override
  public MaxLangCardinality copy() {
    return new MaxLangCardinality(getValueCopy());
  }
}
