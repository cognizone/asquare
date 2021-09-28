package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules;

public class SomeValuesFrom extends Restriction {

  private RdfNode actualValue;

  public SomeValuesFrom() {
  }

  public SomeValuesFrom(Reference property, RdfNode actualValue) {
    super(property);
    this.actualValue = actualValue;
  }

  public SomeValuesFrom(Reference value) {
    super(value);
  }

  @Override
  public Class<Reference> allowedType() {
    return Reference.class;
  }

  @Override
  public SomeValuesFrom copy() {
    return new SomeValuesFrom(getValueCopy(), actualValue.copy());
  }
}
