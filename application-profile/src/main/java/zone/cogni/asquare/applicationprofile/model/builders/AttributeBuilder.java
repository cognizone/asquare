package zone.cogni.asquare.applicationprofile.model.builders;

import com.google.common.base.Preconditions;
import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.basic.def.ApplicationProfileDef;
import zone.cogni.asquare.applicationprofile.rules.Extra;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class AttributeBuilder implements Supplier<ApplicationProfileDef.AttributeDef> {

  private final ApplicationProfileDef.AttributeDef attribute = ApplicationProfileDef.AttributeDef.newInstance();

  public AttributeBuilder withUri(String uri) {
    attribute.setUri(uri);
    return this;
  }

  public AttributeBuilder withAttributeId(String attributeId) {
    attribute.setAttributeId(attributeId);
    return this;
  }

  public AttributeBuilder withRule(Rule rule) {
    attribute.addRule(rule);
    return this;
  }

  public AttributeBuilder withRules(List<Rule> rules) {
    rules.forEach(attribute::addRule);
    return this;
  }

  public AttributeBuilder withExtra(Extra extra) {
    Preconditions.checkNotNull(extra);
    attribute.setExtra(extra);
    return this;
  }

  public String getAttributeId() {
    Objects.requireNonNull(attribute.getAttributeId());
    return attribute.getAttributeId();
  }

  @Override
  public ApplicationProfileDef.AttributeDef get() {
    return attribute;
  }

}
