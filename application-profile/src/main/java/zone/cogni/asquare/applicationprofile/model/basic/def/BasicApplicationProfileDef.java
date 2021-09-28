package zone.cogni.asquare.applicationprofile.model.basic.def;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import zone.cogni.asquare.applicationprofile.rules.Extra;
import zone.cogni.asquare.applicationprofile.rules.PropertyValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public class BasicApplicationProfileDef extends ApplicationProfileDef {

  private final Map<String, TypeDef> types = new TreeMap<>();
  private String uri;
  private Extra extra = new Extra();
  private ApplicationProfileDef parent;
  private List<ApplicationProfileDef> imports = new ArrayList<>();

  @Override
  public String getUri() {
    return uri;
  }

  @Override
  public void setUri(String uri) {
    this.uri = uri;
  }

  @Override
  public Extra getExtra() {
    return extra;
  }

  @Override
  public void setExtra(Extra extra) {
    Preconditions.checkNotNull(extra);
    this.extra = extra;
  }

  @Override
  public void addExtra(String property, String value) {
    List<PropertyValue> result = extra.getValue();
    result.add(new PropertyValue(property, value));

    extra.setValue(result);
  }

  @Override
  public List<ApplicationProfileDef> getImports() {
    return Collections.unmodifiableList(imports);
  }

  @Override
  public void setImports(List<ApplicationProfileDef> applicationProfiles) {
    Objects.requireNonNull(applicationProfiles);

    applicationProfiles.forEach(applicationProfile -> applicationProfile.setParentDefinition(this));
    imports = applicationProfiles;
  }

  @Override
  public void addImport(ApplicationProfileDef applicationProfile) {
    Objects.requireNonNull(applicationProfile);

    applicationProfile.setParentDefinition(this);
    imports.add(applicationProfile);
  }

  @Override
  public boolean hasTypeDef(String typeId) {
    Objects.requireNonNull(typeId);
    Objects.requireNonNull(types);

    return types.containsKey(typeId);
  }

  @Override
  public TypeDef getTypeDef(String typeId) {
    Objects.requireNonNull(typeId);
    Objects.requireNonNull(types);

    TypeDef type = types.get(typeId);
    Objects.requireNonNull(type, "type '" + typeId + "' does not exist");

    return type;
  }

  @Override
  public Map<String, TypeDef> getTypeDefs() {
    return Collections.unmodifiableMap(types);
  }

  @Override
  public void setTypeDefs(Map<String, TypeDef> types) {
    Objects.requireNonNull(types, "types cannot be null");

    types.forEach((key, type) -> type.setClassId(key));
    types.values().forEach(this::ensureApplicationProfileIsSet);

    this.types.forEach(types::put);
  }

  @Override
  public void addTypeDef(TypeDef type) {
    Objects.requireNonNull(type, "type cannot be null");
    Preconditions.checkState(StringUtils.isNotBlank(type.getClassId()), "classId must be set");

    ensureApplicationProfileIsSet(type);

    Preconditions.checkState(!types.containsKey(type.getClassId()),
            "classId " + type.getClassId() + " used more than once");

    types.put(type.getClassId(), type);
  }

  @Override
  public ApplicationProfileDef getParentDefinition() {
    return parent;
  }

  @Override
  public void setParentDefinition(ApplicationProfileDef parentApplicationProfile) {
    Preconditions.checkState(parent == null);
    parent = parentApplicationProfile;
  }

  @Override
  public ApplicationProfileDef getRootDefinition() {
    return this;
  }

  @Override
  public Set<Class<?>> getInternalClassChain() {
    return Collections.singleton(getClass());
  }

  private void ensureApplicationProfileIsSet(TypeDef type) {
    Preconditions.checkState(type.getApplicationProfileDef() == null || type.getApplicationProfileDef() == this);
    if (type.getApplicationProfileDef() == null) type.setApplicationProfileDef(this);
  }


}
