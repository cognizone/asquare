package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules;

public class HasValue extends Restriction {

  private RdfNode actualValue;

  public HasValue() {
  }

  public HasValue(Reference property, RdfNode actualValue) {
    super(property);
    this.actualValue = actualValue;
  }

  public HasValue(Reference value) {
    super(value);
  }

  @Override
  public Class<Reference> allowedType() {
    return Reference.class;
  }

  @Override
  public HasValue copy() {
    return new HasValue(getValueCopy(), actualValue.copy());
  }
}
