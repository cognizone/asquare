package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules;

import zone.cogni.asquare.applicationprofile.model.SingleValueRule;

public class OwlEquivalentClass extends SingleValueRule<OwlClassReference> {

  private OwlClassReference equivalentClass;

  public OwlEquivalentClass() {
  }

  public OwlEquivalentClass(OwlClassReference value, OwlClassReference equivalentClass) {
    super(value);
    this.equivalentClass = equivalentClass;
  }

  @Override
  public Class<OwlClassReference> allowedType() {
    return OwlClassReference.class;
  }

  @Override
  public OwlEquivalentClass copy() {
    return new OwlEquivalentClass(getValueCopy(), equivalentClass.copy());
  }

  public OwlClassReference getEquivalentClass() {
    return equivalentClass;
  }

  public void setEquivalentClass(OwlClassReference equivalentClass) {
    this.equivalentClass = equivalentClass;
  }
}
