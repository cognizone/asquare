package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules;

public class AllValuesFrom extends Restriction {

  private RdfNode actualValue;

  public AllValuesFrom() {
  }

  public AllValuesFrom(Reference property, RdfNode actualValue) {
    super(property);
    this.actualValue = actualValue;
  }

  public AllValuesFrom(Reference value) {
    super(value);
  }

  @Override
  public Class<Reference> allowedType() {
    return Reference.class;
  }

  @Override
  public AllValuesFrom copy() {
    return new AllValuesFrom(getValueCopy(), actualValue.copy());
  }
}
