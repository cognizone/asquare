package zone.cogni.asquare.applicationprofile.owl.owl2ap;

import com.google.common.base.Preconditions;
import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.basic.def.ApplicationProfileDef;
import zone.cogni.asquare.applicationprofile.rules.SubClassOf;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class Check {

  public static <T extends Rule> Function<Check, T> rule(String type, String attribute, Class<T> ruleType) {
    return check -> {
      List<T> rules = check.applicationProfile.getTypeDef(type).getAttributeDef(attribute).getRules(ruleType);
      Preconditions.checkState(rules.size() <= 1);
      return rules.isEmpty() ? null : rules.get(0);
    };
  }

  public static void checkRuleClass(Rule rule, Class<?> type) {
    assertThat(rule.getClass()).isEqualTo(type);
  }

  private final ApplicationProfileDef applicationProfile;

  Check(ApplicationProfileDef applicationProfile) {
    this.applicationProfile = applicationProfile;
  }

  public Check hasType(String type) {
    assertThat(applicationProfile.hasTypeDef(type)).isTrue();
    return this;
  }

  public Check extraSize(String type, int size) {
    assertThat(applicationProfile.getTypeDef(type).getExtra().getValue()).hasSize(size);
    return this;
  }

  public Check hasExtraProperty(String type, String property) {
    boolean match = applicationProfile.getTypeDef(type).getExtra().getValue().stream()
            .anyMatch(propertyValue -> Objects.equals(propertyValue.getProperty(), property));
    assertThat(match).isTrue();
    return this;
  }

  public Check hasRuleOfType(String type, String ruleType) {
    boolean match = applicationProfile.getTypeDef(type).getRules().stream()
            .anyMatch(rule -> Objects.equals(rule.getRuleName(), ruleType));
    assertThat(match).isTrue();
    return this;
  }

  public Check hasSubClassOf(String type, String subclass) {
    boolean match = applicationProfile.getTypeDef(type).getRules(SubClassOf.class).stream()
            .anyMatch(subClassOf -> subClassOf.getValue().contains(subclass));
    assertThat(match).isTrue();
    return this;
  }

  public Check hasAttribute(String type, String attribute) {
    assertThat(applicationProfile.getTypeDef(type).hasAttributeDef(attribute)).isTrue();
    return this;
  }

  public Check attributeRuleSize(String type, String attribute, int size) {
    assertThat(applicationProfile.getTypeDef(type).getAttributeDef(attribute).getRules()).hasSize(size);
    return this;
  }

  public Check hasRuleOfType(String type, String attribute, String ruleType) {
    boolean match = applicationProfile.getTypeDef(type).getAttributeDef(attribute)
            .getRules().stream()
            .anyMatch(rule -> Objects.equals(rule.getRuleName(), ruleType));
    assertThat(match).isTrue();
    return this;
  }
}
