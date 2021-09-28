package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules;

import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.SingleValueRule;

import java.util.Objects;

public class PropertyDomain extends SingleValueRule<PropertyReference> {

  private Rule domain;

  public PropertyDomain() {
  }

  public PropertyDomain(PropertyReference value, Rule domain) {
    super(value.copy());
    this.domain = domain == null ? null : domain.copy();
  }

  public Rule getDomain() {
    return domain;
  }

  public void setDomain(Rule domain) {
    this.domain = domain;
  }

  @Override
  public Class<PropertyReference> allowedType() {
    return PropertyReference.class;
  }

  @Override
  public PropertyDomain copy() {
    return new PropertyDomain(getValueCopy(), domain == null ? null : domain.copy());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    PropertyDomain that = (PropertyDomain) o;
    return Objects.equals(domain, that.domain);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), domain);
  }
}
