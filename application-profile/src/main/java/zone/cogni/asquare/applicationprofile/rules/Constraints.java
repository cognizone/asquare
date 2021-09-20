package zone.cogni.asquare.applicationprofile.rules;

import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.SingleValueRule;

public class Constraints extends SingleValueRule<Rule> {

  public Constraints() {
  }

  public Constraints(Rule value) {
    super(value);
  }

  @Override
  public Class<Rule> allowedType() {
    return Rule.class;
  }

  @Override
  public Constraints copy() {
    return new Constraints(getValueCopy());
  }
}
