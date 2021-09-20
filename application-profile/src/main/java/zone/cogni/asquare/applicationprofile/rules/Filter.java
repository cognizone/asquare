package zone.cogni.asquare.applicationprofile.rules;

import zone.cogni.asquare.applicationprofile.model.ListSingleValueRule;
import zone.cogni.asquare.applicationprofile.model.Rule;

import java.util.List;

// not used ?!!
public class Filter extends ListSingleValueRule<Rule> {

  public Filter() {
  }

  public Filter(List<Rule> value) {
    super(value);
  }

  @Override
  public Class<Rule> allowedType() {
    return Rule.class;
  }

  @Override
  public Filter copy() {
    return new Filter(getValueCopy());
  }
}
