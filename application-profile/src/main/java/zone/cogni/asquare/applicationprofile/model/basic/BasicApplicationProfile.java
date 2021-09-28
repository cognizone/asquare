package zone.cogni.asquare.applicationprofile.model.basic;

import zone.cogni.asquare.applicationprofile.model.basic.def.ApplicationProfileDef;
import zone.cogni.asquare.applicationprofile.rules.Extra;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BasicApplicationProfile extends ApplicationProfile {

  private final ApplicationProfileDef applicationProfile;

  public BasicApplicationProfile(ApplicationProfileDef applicationProfile) {
    this.applicationProfile = applicationProfile;
  }

  @Override
  public String getUri() {
    return applicationProfile.getUri();
  }

  @Override
  public Extra getExtra() {
    return applicationProfile.getExtra();
  }

  @Override
  public boolean hasType(String typeId) {
    return applicationProfile.hasTypeDef(typeId);
  }

  @Override
  public Type getType(String typeId) {
    ApplicationProfileDef.TypeDef type = applicationProfile.getTypeDef(typeId);
    return new BasicType(type);
  }

  @Override
  public Map<String, Type> getTypes() {
    Map<String, Type> readonly = new HashMap<>();
    applicationProfile.getTypeDefs()
            .forEach((k, v) -> readonly.put(k, new BasicType(v)));
    return readonly;
  }

  @Override
  public ApplicationProfileDef getApplicationProfileDef() {
    return applicationProfile;
  }

  @Override
  public Set<Class<?>> getInternalClassChain() {
    Set<Class<?>> result = new HashSet<>();
    result.addAll(applicationProfile.getInternalClassChain());
    result.add(getClass());
    return result;
  }
}
