package zone.cogni.asquare.applicationprofile.model.builders;

import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.basic.def.ApplicationProfileDef;
import zone.cogni.asquare.applicationprofile.rules.Extra;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class TypeBuilder implements Supplier<ApplicationProfileDef.TypeDef> {

  private final ApplicationProfileDef.TypeDef type = ApplicationProfileDef.TypeDef.newInstance();

  public TypeBuilder withExtra(Extra extra) {
    type.setExtra(extra);
    return this;
  }

  public TypeBuilder withClassId(String classId) {
    type.setClassId(classId);
    return this;
  }

  public TypeBuilder withAttribute(AttributeBuilder attributeBuilder) {
    type.addAttributeDef(attributeBuilder.get());
    return this;
  }

  public TypeBuilder withRule(Rule rule) {
    type.addRule(rule);
    return this;
  }

  public TypeBuilder withRules(List<Rule> rules) {
    rules.forEach(type::addRule);
    return this;
  }

  public String getClassId() {
    Objects.requireNonNull(type.getClassId());
    return type.getClassId();
  }

  @Override
  public ApplicationProfileDef.TypeDef get() {
    return type;
  }
}
