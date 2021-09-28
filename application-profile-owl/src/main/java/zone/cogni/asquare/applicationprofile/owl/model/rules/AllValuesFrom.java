package zone.cogni.asquare.applicationprofile.owl.model.rules;

import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.SingleValueRule;

public class AllValuesFrom extends SingleValueRule<Rule> {

  public AllValuesFrom() {
  }

  public AllValuesFrom(Rule value) {
    super(value);
  }

  @Override
  public Class<Rule> allowedType() {
    return Rule.class;
  }

  @Override
  public AllValuesFrom copy() {
    return new AllValuesFrom(getValueCopy());
  }

}
