package zone.cogni.asquare.applicationprofile.owl.model.rules;

import zone.cogni.asquare.applicationprofile.model.Rule;

public class QualifiedCardinality implements QualifiedCardinalityRule {

  private Integer value;
  private Rule qualification;

  public QualifiedCardinality() {
  }

  public QualifiedCardinality(Integer value, Rule qualification) {
    this.value = value;
    this.qualification = qualification;
  }

  @Override
  public Integer getValue() {
    return value;
  }

  @Override
  public void setValue(Integer value) {
    this.value = value;
  }

  @Override
  public Rule getQualification() {
    return qualification;
  }

  @Override
  public void setQualification(Rule qualification) {
    this.qualification = qualification;
  }

  @Override
  public QualifiedCardinality copy() {
    return new QualifiedCardinality(value, qualification.copy());
  }

}
