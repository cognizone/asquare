package zone.cogni.asquare.applicationprofile.owl.model.rules;

import zone.cogni.asquare.applicationprofile.model.Rule;

public interface QualifiedCardinalityRule extends Rule {

  Integer getValue() ;

  void setValue(Integer value) ;

  Rule getQualification();

  void setQualification(Rule qualification);

}
