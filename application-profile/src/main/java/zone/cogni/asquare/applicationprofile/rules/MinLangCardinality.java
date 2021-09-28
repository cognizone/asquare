package zone.cogni.asquare.applicationprofile.rules;

import zone.cogni.asquare.applicationprofile.model.SingleValueRule;

public class MinLangCardinality extends SingleValueRule<Integer> {

  public MinLangCardinality() {
  }

  public MinLangCardinality(Integer value) {
    super(value);
  }

  @Override
  public Class<Integer> allowedType() {
    return Integer.class;
  }

  @Override
  public MinLangCardinality copy() {
    return new MinLangCardinality(getValueCopy());

  }
}
