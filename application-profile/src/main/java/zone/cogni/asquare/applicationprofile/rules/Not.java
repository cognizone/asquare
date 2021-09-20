package zone.cogni.asquare.applicationprofile.rules;

import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.SingleValueRule;

public class Not extends SingleValueRule<Rule> {

  public Not() {
  }

  public Not(Rule value) {
    super(value);
  }

  @Override
  public Class<Rule> allowedType() {
    return Rule.class;
  }

  @Override
  public Not copy() {
    return new Not(getValueCopy());
  }
}
