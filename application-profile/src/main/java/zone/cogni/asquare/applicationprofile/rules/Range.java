package zone.cogni.asquare.applicationprofile.rules;

import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.SingleValueRule;

/**
 * allowed values:
 *   classId
 *   intersectionOf
 *   unionOf
 *
 * all of which are constraints on type of attribute in case of attributes
 *
 * other allowed value:
 *   inScheme
 * which is a constraint on another attribute (not type)
 *
 */
public class Range extends SingleValueRule<Rule> {

  public Range() {
  }

  public Range(Rule value) {
    super(value);
  }

  @Override
  public Class<Rule> allowedType() {
    return Rule.class;
  }

  @Override
  public Range copy() {
    return new Range(getValueCopy());
  }

}
