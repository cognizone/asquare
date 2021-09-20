package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules;

public class OwlMaxCardinality extends OwlCardinality {

  public OwlMaxCardinality() {
  }

  public OwlMaxCardinality(Reference type) {
    super(type);
  }

  public OwlMaxCardinality(Reference type, Reference property, int cardinality) {
    super(type, property, cardinality);
  }

  @Override
  public OwlMaxCardinality copy() {
    return new OwlMaxCardinality(getValueCopy(), getProperty().copy(), getCardinality());
  }

}
