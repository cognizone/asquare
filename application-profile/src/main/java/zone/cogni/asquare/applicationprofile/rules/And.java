package zone.cogni.asquare.applicationprofile.rules;

import zone.cogni.asquare.applicationprofile.model.ListSingleValueRule;
import zone.cogni.asquare.applicationprofile.model.Rule;

import java.util.List;

public class And extends ListSingleValueRule<Rule> {

  public And() {
  }

  public And(List<Rule> value) {
    super(value);
  }

  @Override
  public Class<Rule> allowedType() {
    return Rule.class;
  }

  @Override
  public And copy() {
    return new And(getValueCopy());
  }
}
