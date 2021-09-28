package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl;

import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.basic.def.ApplicationProfileDef;
import zone.cogni.asquare.applicationprofile.rules.And;

import java.util.ArrayList;
import java.util.List;

/*
Algorithm:

- get top properties (no super) without inverses
    - process them into classes
- get top properties with inverse
    - process domain / range into each other
- recurse !

 */
class IntermediateAttribute {

  public enum Type {object, datatype, unknown}


  private final ApplicationProfileDef.AttributeDef attributeDef;
  private final Type type;
  private Rule domain;
  private Rule range;

  public IntermediateAttribute(ApplicationProfileDef.AttributeDef attributeDef, Type type) {
    this.attributeDef = attributeDef;
    this.type = type;
  }

  public ApplicationProfileDef.AttributeDef getAttributeDef() {
    return attributeDef;
  }

  public Type getType() {
    return type;
  }

  public Rule getDomain() {
    return domain;
  }

  public void addDomain(List<Rule> rules) {
    rules.forEach(this::addDomain);
  }

  private void addDomain(Rule newRule) {
    this.domain = getMerged(this.domain, newRule);
  }

  public Rule getRange() {
    return range;
  }

  public void addRange(List<Rule> rules) {
    rules.forEach(this::addRange);
  }

  private void addRange(Rule newRule) {
    this.range = getMerged(this.range, newRule);
  }

  private Rule getMerged(Rule oldRule, Rule newRule) {
    if (oldRule == null) {
      return newRule;
    }

    return getMergedDomains(oldRule, newRule);
  }

  private And getMergedDomains(Rule oldRule, Rule newRule) {
    And and = new And();
    List<Rule> rules = new ArrayList<>();
    rules.add(oldRule);
    rules.add(newRule);
    and.setValue(rules);
    return and;
  }

}
