package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules;

import zone.cogni.asquare.applicationprofile.model.Rule;

public class ExactQualifiedCardinality extends QualifiedCardinality {

  public ExactQualifiedCardinality() {
  }

  public  ExactQualifiedCardinality(Reference type) {
    super(type);
  }

  public ExactQualifiedCardinality(Reference type, Reference property, int cardinality, Rule range) {
    super(type, property, cardinality, range);
  }

  @Override
  public ExactQualifiedCardinality copy() {
    return new ExactQualifiedCardinality(getValueCopy(), getProperty().copy(), getCardinality(), getQualification().copy());
  }

}
