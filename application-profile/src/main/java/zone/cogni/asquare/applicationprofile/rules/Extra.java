package zone.cogni.asquare.applicationprofile.rules;

import zone.cogni.asquare.applicationprofile.model.ListSingleValueRule;

import java.util.Collections;
import java.util.List;

public class Extra extends ListSingleValueRule<PropertyValue> {

  public Extra() {
  }

  public Extra(List<PropertyValue> value) {
    super(value);
  }

  @Override
  public Class<PropertyValue> allowedType() {
    return PropertyValue.class;
  }

  @Override
  public void setValue(List<PropertyValue> list) {
    Collections.sort(list);
    super.setValue(list);
  }

  @Override
  public Extra copy() {
    return new Extra(getValueCopy());
  }
}
