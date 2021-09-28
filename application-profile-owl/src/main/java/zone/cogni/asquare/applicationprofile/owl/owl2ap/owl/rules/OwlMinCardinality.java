package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules;

public class OwlMinCardinality extends OwlCardinality {

  public OwlMinCardinality() {
  }

  public OwlMinCardinality(Reference type) {
    super(type);
  }

  public OwlMinCardinality(Reference type, Reference property, int cardinality) {
    super(type, property, cardinality);
  }

  @Override
  public OwlMinCardinality copy() {
    return new OwlMinCardinality(getValueCopy(), getProperty().copy(), getCardinality());
  }
}
