package zone.cogni.asquare.applicationprofile.owl.model.rules;

import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.SingleValueRule;

// rule is actually a range
public class SomeValuesFrom extends SingleValueRule<Rule> {

  public SomeValuesFrom() {
  }

  public SomeValuesFrom(Rule value) {
    super(value);
  }

  @Override
  public Class<Rule> allowedType() {
    return Rule.class;
  }

  @Override
  public SomeValuesFrom copy() {
    return new SomeValuesFrom(getValueCopy());
  }
}
