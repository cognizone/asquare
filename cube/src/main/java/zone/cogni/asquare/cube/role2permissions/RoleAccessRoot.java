package zone.cogni.asquare.cube.role2permissions;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamSource;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class RoleAccessRoot {
  private static final Logger log = LoggerFactory.getLogger(RoleAccessRoot.class);

  public static RoleAccessRoot load(InputStreamSource resource, Set<String> operationIds) {
    try {
      log.info("load json {}", resource);
      ObjectMapper objectMapper = new ObjectMapper();
      objectMapper.enable(JsonParser.Feature.ALLOW_COMMENTS);

      RoleAccessRoot result = objectMapper.readValue(resource.getInputStream(), RoleAccessRoot.class);

      result.checkUnusedOperations(operationIds);
      result.checkAllPermissionsExistAsOperations(operationIds);

      log.info("load role access json done");

      return result;
    }
    catch (IOException e) {
      throw new RuntimeException("Unable to load role access configuration.", e);
    }
  }

  private Set<String> rulesDefault;
  private Map<String, Role> roles;

  public Set<String> getRulesDefault() {
    return rulesDefault;
  }

  public Map<String, Role> getRoles() {
    return roles;
  }

  private void checkUnusedOperations(Set<String> operationIds) {
    Sets.difference(operationIds, getRuleIds())
        .forEach(rule -> log.warn("Unused operation found: {}", rule));
  }

  private void checkAllPermissionsExistAsOperations(Set<String> operationIds) {
    Set<String> excess = Sets.difference(getRuleIds(), operationIds);
    if (!excess.isEmpty())
      throw new RuntimeException("The following permissions do not exist in operations : \n" + String.join("\n", excess));
  }

  public Set<String> getRuleIds() {
    return getRoles()
      .values()
      .stream()
      .map(Role::getRules)
      .flatMap(Collection::stream)
      .collect(Collectors.toSet());
  }
}
