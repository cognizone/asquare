package zone.cogni.asquare.applicationprofile.rules;

import zone.cogni.asquare.applicationprofile.model.ListSingleValueRule;
import zone.cogni.asquare.applicationprofile.model.Rule;

import java.util.List;

public class Or extends ListSingleValueRule<Rule> {

  public Or() {
  }

  public Or(List<Rule> value) {
    super(value);
  }

  @Override
  public Class<Rule> allowedType() {
    return Rule.class;
  }

  @Override
  public Or copy() {
    return new Or(getValueCopy());
  }
}
