package zone.cogni.asquare.applicationprofile.model.builders;

import zone.cogni.asquare.applicationprofile.model.basic.def.ApplicationProfileDef;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcService;
import zone.cogni.asquare.applicationprofile.rules.Extra;

import java.util.function.Supplier;

public class ApplicationProfileBuilder implements Supplier<ApplicationProfileDef> {

  private final ApplicationProfileDef applicationProfile = ApplicationProfileDef.newInstance();

  private PrefixCcService prefixCcService;

  public ApplicationProfileBuilder withUri(String uri) {
    applicationProfile.setUri(uri);
    return this;
  }

  public ApplicationProfileBuilder withType(TypeBuilder typeBuilder) {
    applicationProfile.addTypeDef(typeBuilder.get());
    return this;
  }

  public ApplicationProfileBuilder withImport(ApplicationProfileDef importApplicationProfile) {
    applicationProfile.addImport(importApplicationProfile);
    return this;
  }

  public ApplicationProfileBuilder withExtra(Extra extra) {
    applicationProfile.setExtra(extra);
    return this;
  }

  @Override
  public ApplicationProfileDef get() {
    return applicationProfile;
  }

  public ApplicationProfileBuilder withPrefixCcService(PrefixCcService prefixCcService) {
    this.prefixCcService = prefixCcService;
    return this;
  }


}
