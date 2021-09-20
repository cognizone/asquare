package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules;

import zone.cogni.asquare.applicationprofile.model.Rule;

public class MaxQualifiedCardinality extends QualifiedCardinality {

  public MaxQualifiedCardinality() {
  }

  public MaxQualifiedCardinality(Reference type) {
    super(type);
  }

  public MaxQualifiedCardinality(Reference type, Reference property, int cardinality, Rule range) {
    super(type, property, cardinality, range);
  }

  @Override
  public MaxQualifiedCardinality copy() {
    return new MaxQualifiedCardinality(getValueCopy(), getProperty().copy(), getCardinality(), getQualification().copy());
  }

}
