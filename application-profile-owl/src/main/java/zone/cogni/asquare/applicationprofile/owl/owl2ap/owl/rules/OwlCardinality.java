package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules;

public abstract class OwlCardinality extends Restriction {

  private Reference property;
  private int cardinality;

  public OwlCardinality() {
  }

  public OwlCardinality(Reference type) {
    super(type);
  }

  public OwlCardinality(Reference type, Reference property, int cardinality) {
    super(type);
    this.property = property;
    this.cardinality = cardinality;
  }

  @Override
  public Class<Reference> allowedType() {
    return Reference.class;
  }

  public Reference getProperty() {
    return property;
  }

  public void setProperty(Reference property) {
    this.property = property;
  }

  public int getCardinality() {
    return cardinality;
  }

  public void setCardinality(int cardinality) {
    this.cardinality = cardinality;
  }

  @Override
  public String toString() {
    return "OwlCardinality{" +
           " \n\tvalue       = " + getValue() +
           ",\n\tproperty    =" + property +
           ",\n\tcardinality =" + cardinality +
           '}';
  }
}
