package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules;

import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.SingleValueRule;

import java.util.Objects;

public class PropertyRange extends SingleValueRule<PropertyReference> {

  private Rule range;

  public PropertyRange() {
  }

  public PropertyRange(PropertyReference value, Rule range) {
    super(value.copy());
    this.range = range == null ? null : range.copy();
  }

  public Rule getRange() {
    return range;
  }

  public void setRange(Rule range) {
    this.range = range;
  }

  @Override
  public Class<PropertyReference> allowedType() {
    return PropertyReference.class;
  }

  @Override
  public PropertyRange copy() {
    return new PropertyRange(getValueCopy(), range == null ? null : range.copy());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    PropertyRange that = (PropertyRange) o;
    return Objects.equals(range, that.range);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), range);
  }
}
