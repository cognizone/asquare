package zone.cogni.asquare.applicationprofile.owl.model.rules;

import zone.cogni.asquare.applicationprofile.model.ListSingleValueRule;

import java.util.List;

public class DisjointWith extends ListSingleValueRule<String> {

  public DisjointWith() {
  }

  public DisjointWith(List<String> value) {
    super(value);
  }

  @Override
  public Class<String> allowedType() {
    return String.class;
  }

  @Override
  public DisjointWith copy() {
    return new DisjointWith(getValueCopy());
  }

}
