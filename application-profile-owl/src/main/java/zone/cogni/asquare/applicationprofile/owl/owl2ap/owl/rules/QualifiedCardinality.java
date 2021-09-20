package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules;

import zone.cogni.asquare.applicationprofile.model.Rule;

public abstract class QualifiedCardinality extends Restriction {

  private Reference property;
  private int cardinality;
  private Rule qualification;

  public QualifiedCardinality() {
  }

  public QualifiedCardinality(Reference type) {
    super(type);
  }

  public QualifiedCardinality(Reference type, Reference property, int cardinality, Rule qualification) {
    super(type);
    this.property = property;
    this.cardinality = cardinality;
    this.qualification = qualification;
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

  public Rule getQualification() {
    return qualification;
  }

  public void setQualification(Rule qualification) {
    this.qualification = qualification;
  }
}
