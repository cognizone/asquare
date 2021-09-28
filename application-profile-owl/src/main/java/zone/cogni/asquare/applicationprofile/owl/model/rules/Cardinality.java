package zone.cogni.asquare.applicationprofile.owl.model.rules;

import zone.cogni.asquare.applicationprofile.model.SingleValueRule;

public class Cardinality extends SingleValueRule<Integer> {

  public Cardinality() {
  }

  public Cardinality(Integer value) {
    super(value);
  }

  @Override
  public Class<Integer> allowedType() {
    return Integer.class;
  }

  @Override
  public Cardinality copy() {
    return new Cardinality(getValueCopy());
  }

}
