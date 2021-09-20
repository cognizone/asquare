package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules;

import zone.cogni.asquare.applicationprofile.model.SingleValueRule;

import java.util.Objects;

public class OwlSubClassOf extends SingleValueRule<OwlClassReference> {

  private OwlClassReference superClass;

  public OwlSubClassOf() {
  }

  public OwlSubClassOf(OwlClassReference value, OwlClassReference superClass) {
    super(value.copy());
    this.superClass = superClass.copy();
  }

  public OwlClassReference getSuperClass() {
    return superClass;
  }

  public void setSuperClass(OwlClassReference superClass) {
    this.superClass = superClass;
  }

  @Override
  public Class<OwlClassReference> allowedType() {
    return OwlClassReference.class;
  }

  @Override
  public OwlSubClassOf copy() {
    return new OwlSubClassOf(getValueCopy(), superClass.copy());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    OwlSubClassOf that = (OwlSubClassOf) o;
    return Objects.equals(superClass, that.superClass);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), superClass);
  }
}
