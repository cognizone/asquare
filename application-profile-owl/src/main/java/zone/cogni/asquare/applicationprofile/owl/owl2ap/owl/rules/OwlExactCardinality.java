package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules;

public class OwlExactCardinality extends OwlCardinality {

  public OwlExactCardinality() {
  }

  public OwlExactCardinality(Reference type) {
    super(type);
  }

  public OwlExactCardinality(Reference type, Reference property, int cardinality) {
    super(type, property, cardinality);
  }

  @Override
  public OwlExactCardinality copy() {
    return new OwlExactCardinality(getValueCopy(), getProperty().copy(), getCardinality());
  }

}
