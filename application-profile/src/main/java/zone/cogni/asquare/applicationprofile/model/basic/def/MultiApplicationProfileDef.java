package zone.cogni.asquare.applicationprofile.model.basic.def;

import com.google.common.base.Preconditions;
import zone.cogni.asquare.applicationprofile.rules.Extra;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MultiApplicationProfileDef extends ApplicationProfileDef {

  public static MultiApplicationProfileDef newInstance(ApplicationProfileDef rootApplicationProfile) {
    return new MultiApplicationProfileDef(rootApplicationProfile);
  }

  private final ApplicationProfileDef top;
  private final List<ApplicationProfileDef> applicationProfiles;

  private MultiApplicationProfileDef(@Nonnull ApplicationProfileDef top) {
    this.top = top;
    applicationProfiles = calculateApplicationProfiles(top);
  }

  private List<ApplicationProfileDef> calculateApplicationProfiles(ApplicationProfileDef root) {
    List<ApplicationProfileDef> all = new ArrayList<>();
    calculateApplicationProfiles(root, all);
    return all;
  }

  private void calculateApplicationProfiles(ApplicationProfileDef current, List<ApplicationProfileDef> all) {
    all.add(current);
    current.getImports().forEach(ap -> calculateApplicationProfiles(ap, all));
  }

  @Override
  public String getUri() {
    Set<String> uris = applicationProfiles.stream()
            .map(ApplicationProfileDef::getUri)
            .collect(Collectors.toSet());

    Preconditions.checkState(uris.size() == 1,
            "currently only merged application profiles with same 'uri' are supported");
    return uris.stream().findFirst().get();
  }

  @Override
  public void setUri(String uri) {
    throw new UnsupportedOperationException("MultiApplicationProfile is readonly");
  }

  @Override
  public Extra getExtra() {
    throw new UnsupportedOperationException("Extra data is only relevant in a serialization context.");
  }

  @Override
  public void setExtra(Extra extra) {
    throw new UnsupportedOperationException("MultiApplicationProfile is readonly");
  }

  @Override
  public void addExtra(String property, String value) {
    throw new UnsupportedOperationException("MultiApplicationProfile is readonly");
  }

  @Override
  public List<ApplicationProfileDef> getImports() {
    throw new UnsupportedOperationException("Imports are only relevant in a serialization context.");
  }

  @Override
  public void addImport(ApplicationProfileDef applicationProfile) {
    throw new UnsupportedOperationException("MultiApplicationProfile is readonly");
  }

  @Override
  public void setImports(List<ApplicationProfileDef> applicationProfiles) {
    throw new UnsupportedOperationException("MultiApplicationProfile is readonly");
  }

  @Override
  public boolean hasTypeDef(String typeId) {
    return applicationProfiles.stream()
            .anyMatch(ap -> ap.hasTypeDef(typeId));
  }

  @Override
  public TypeDef getTypeDef(String typeId) {
    Stream<TypeDef> types = applicationProfiles.stream()
            .filter(ap -> ap.hasTypeDef(typeId))
            .map(ap -> ap.getTypeDef(typeId));

    return MultiTypeDef.newInstance(this, types);
  }

  @Override
  public Map<String, TypeDef> getTypeDefs() {
    Map<String, TypeDef> result = new HashMap<>();

    applicationProfiles.stream()
            .flatMap(ap -> ap.getTypeDefs().keySet().stream())
            .distinct()
            .forEach(typeId -> result.put(typeId, getTypeDef(typeId)));

    return result;
  }

  @Override
  public void setTypeDefs(Map<String, TypeDef> types) {
    throw new UnsupportedOperationException("MultiApplicationProfile is readonly");
  }

  @Override
  public void addTypeDef(TypeDef type) {
    throw new UnsupportedOperationException("MultiApplicationProfile is readonly");
  }

  @Override
  public ApplicationProfileDef getParentDefinition() {
    return null;
  }

  @Override
  public void setParentDefinition(ApplicationProfileDef parentApplicationProfile) {
    throw new UnsupportedOperationException("MultiApplicationProfile is root and cannot have a parent");
  }

  @Override
  public ApplicationProfileDef getRootDefinition() {
    return top.getRootDefinition();
  }

  @Override
  public Set<Class<?>> getInternalClassChain() {
    Set<Class<?>> result = new HashSet<>();
    result.addAll(top.getInternalClassChain());
    result.add(getClass());
    return result;
  }

  @Override
  public int hashCode() {
    // hash code is calculated based on sum of hash codes of profile URLs
    // arithmetic sum makes hash sum сommutative
    // it is independent of an order how application profiles appear in list
    // hash("url1","url2") == hash("url2","url1")
    return Objects.hash(applicationProfiles.stream() .map(ApplicationProfileDef::getUri).map(Objects::hash).reduce(0, Integer::sum));
  }
}
