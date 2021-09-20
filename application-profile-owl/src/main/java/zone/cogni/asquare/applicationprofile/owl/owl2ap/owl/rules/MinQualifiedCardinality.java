package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules;

import zone.cogni.asquare.applicationprofile.model.Rule;

public class MinQualifiedCardinality extends QualifiedCardinality {

  public MinQualifiedCardinality() {
  }

  public MinQualifiedCardinality(Reference type) {
    super(type);
  }

  public MinQualifiedCardinality(Reference type, Reference property, int cardinality, Rule range) {
    super(type, property, cardinality, range);
  }

  @Override
  public MinQualifiedCardinality copy() {
    return new MinQualifiedCardinality(getValueCopy(), getProperty().copy(), getCardinality(), getQualification().copy());
  }

}
