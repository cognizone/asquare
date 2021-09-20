package zone.cogni.asquare.applicationprofile.rules;

import zone.cogni.asquare.applicationprofile.model.ListSingleValueRule;

import java.util.List;

public class SubClassOf extends ListSingleValueRule<String> {

  public SubClassOf() {
  }

  public SubClassOf(List<String> value) {
    super(value);
  }

  @Override
  public Class<String> allowedType() {
    return String.class;
  }

  @Override
  public SubClassOf copy() {
    return new SubClassOf(getValueCopy());
  }
}
