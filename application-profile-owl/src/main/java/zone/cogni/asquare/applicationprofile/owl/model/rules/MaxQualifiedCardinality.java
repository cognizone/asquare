package zone.cogni.asquare.applicationprofile.owl.model.rules;

import zone.cogni.asquare.applicationprofile.model.Rule;

public class MaxQualifiedCardinality implements QualifiedCardinalityRule {

  private Integer value;
  private Rule qualification;

  public MaxQualifiedCardinality() {
  }

  public MaxQualifiedCardinality(Integer value, Rule qualification) {
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
  public MaxQualifiedCardinality copy() {
    return new MaxQualifiedCardinality(value, qualification.copy());
  }

  @Override
  public String toString() {
    return getRuleName()
           + "{ "
           + "value:" + value
           + ", qualification: " + qualification
           + " }";
  }

}
