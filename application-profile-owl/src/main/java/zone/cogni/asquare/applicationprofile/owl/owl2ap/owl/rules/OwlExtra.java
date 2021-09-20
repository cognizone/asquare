package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules;

import zone.cogni.asquare.applicationprofile.model.SingleValueRule;

import java.util.List;
import java.util.stream.Collectors;

public class OwlExtra extends SingleValueRule<Reference> {

  private Reference property;
  private List<RdfNode> values;

  public OwlExtra() {
  }

  public OwlExtra(Reference value, Reference property, List<RdfNode> values) {
    super(value);
    this.property = property;
    this.values = values;
  }

  @Override
  public Class<Reference> allowedType() {
    return Reference.class;
  }

  @Override
  public OwlExtra copy() {
    return new OwlExtra(getValueCopy(), property.copy(), values.stream()
                                                               .map(SingleValueRule::getSingleValueCopy)
                                                               .collect(Collectors.toList()));
  }

  public Reference getProperty() {
    return property;
  }

  public void setProperty(Reference property) {
    this.property = property;
  }

  public List<RdfNode> getValues() {
    return values;
  }

  public void setValues(List<RdfNode> values) {
    this.values = values;
  }
}
