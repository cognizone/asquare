package zone.cogni.asquare.applicationprofile.model.basic;

import io.vavr.control.Option;
import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.basic.def.ApplicationProfileDef;
import zone.cogni.asquare.applicationprofile.rules.Extra;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class BasicAttribute extends ApplicationProfile.Attribute {

  private final ApplicationProfileDef.AttributeDef attribute;

  public BasicAttribute(ApplicationProfileDef.AttributeDef attribute) {
    this.attribute = attribute;
  }

  @Override
  public String getUri() {
    return attribute.getUri();
  }

  @Override
  public ApplicationProfile.Type getType() {
    ApplicationProfileDef.TypeDef type = attribute.getTypeDef();
    return new BasicType(type);
  }

  @Override
  public String getAttributeId() {
    return attribute.getAttributeId();
  }

  @Override
  public Extra getExtra() {
    return attribute.getExtra();
  }

  @Override
  public <T extends Rule> Option<T> getRule(Class<T> type) {
    return attribute.getRule(type);
  }

  @Override
  public <T extends Rule> List<T> getRules(Class<T> type) {
    return attribute.getRules(type);
  }

  @Override
  public List<Rule> getRules() {
    return attribute.getRules();
  }

  @Override
  public Set<ApplicationProfileDef.AttributeDef> getAttributeDef() {
    return Collections.singleton(attribute);
  }
}
