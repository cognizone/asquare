package zone.cogni.asquare.security.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service("permissionService")
public class PermissionService<T extends Enum> {
  private static final Logger log = LoggerFactory.getLogger(PermissionService.class);
  private final Map<String, Set<String>> blacklistProperties = new HashMap<>();
  private final Map<String, Set<String>> notBlacklistedForRole = new HashMap<>();
  private final Map<String, String> ldap2roleName = new HashMap<>();
  private final Map<String, Set<T>> roleName2permissions = Collections.synchronizedMap(new HashMap<>());
  private final Class<T> enumClass;
  private Set<T> defaultPermissions;
  private Set<String> allBlacklistProperties;
  @Value("${asquare.security.role_group.resource:/role-group.json}")
  private String ldapRoleGroupResource;

  public PermissionService(Class<T> enumClass) {
    this.enumClass = enumClass;
  }

  @PostConstruct
  @SuppressWarnings("unchecked")
  private void init() throws Exception {
    log.info("Using ldapRoleGroupResource '{}'", ldapRoleGroupResource);

    try (InputStream inputStream = PermissionService.class.getResourceAsStream(ldapRoleGroupResource)) {
      Map<String, String> jsonData = new ObjectMapper().enable(JsonParser.Feature.ALLOW_COMMENTS).readValue(inputStream, Map.class);
      for (Map.Entry<String, String> entry : jsonData.entrySet()) {
        ldap2roleName.put(StringUtils.lowerCase(entry.getKey()), StringUtils.lowerCase(entry.getValue()));
      }
    }

    try (InputStream inputStream = PermissionService.class.getResourceAsStream("/role_access.json")) {
      Map jsonData = new ObjectMapper().readValue(inputStream, Map.class);

      defaultPermissions = jsonArray2ProjectPermissionSet(jsonData.get("rulesDefault"));

      setBlacklistProperties((Map) jsonData.get("blacklistProperties"));

      Map<String, Map<String, Object>> roles = (Map<String, Map<String, Object>>) jsonData.get("roles");
      for (Map.Entry<String, Map<String, Object>> entry : roles.entrySet()) {
        String role = entry.getKey();
        Map<String, Object> values = entry.getValue();
        if (values.containsKey("notBlacklisted")) {
          Set<String> notBlacklisted = ((Collection<String>) values.get("notBlacklisted")).stream().map(blacklistProperties::get).flatMap(Collection::stream).collect(Collectors.toSet());
          notBlacklistedForRole.put(role, notBlacklisted);
        }
        else {
          notBlacklistedForRole.put(role, Collections.emptySet());
        }

        roleName2permissions.put(StringUtils.lowerCase(role), jsonArray2ProjectPermissionSet(values.get("rules")));
      }
      allBlacklistProperties = Collections.synchronizedSet(blacklistProperties.values().stream().flatMap(Collection::stream).collect(Collectors.toSet()));
    }
  }

  public Set<String> getRoleNames() {
    return roleName2permissions.keySet();
  }

  public Set<T> getProjectPermissions(String roleName) {
    return roleName2permissions.get(StringUtils.lowerCase(roleName));
  }

  public Optional<String> getRoleNameForLdapGroup(String ldapGroup) {
    return Optional.ofNullable(ldap2roleName.get(StringUtils.lowerCase(ldapGroup.toLowerCase())));
  }

  private void setBlacklistProperties(Map<?, ?> blacklistPropertiesMap) {
    if (null == blacklistPropertiesMap) return;
    for (Map.Entry<?, ?> entry : blacklistPropertiesMap.entrySet()) {
      //noinspection unchecked
      blacklistProperties.put((String) entry.getKey(), new HashSet<>((Collection<String>) entry.getValue()));
    }
  }

  private Set<T> jsonArray2ProjectPermissionSet(Object jsonArrayObject) {
    if (null == jsonArrayObject) return Collections.emptySet();
    //noinspection unchecked

    Collection<?> jsonArray = (Collection<?>) jsonArrayObject;
    Set<T> result = new HashSet<>();
    for (Object value : jsonArray) {
      @SuppressWarnings("unchecked")
      T enumValue = (T) Enum.valueOf(enumClass, (String) value);
      result.add(enumValue);
    }
    return result;

//    return (Set<T>) Collections.synchronizedSet(((Collection<?>) jsonArray).stream().map(value -> Enum.valueOf(enumClass, (String) value)).collect(Collectors.toSet()));
  }

  public Set<String> getBlacklistedProperties(Authentication authentication) {
    Set<String> notBlacklistProperties = getRoles(authentication).stream()
                                                                 .map(notBlacklistedForRole::get)
                                                                 .filter(Objects::nonNull)
                                                                 .flatMap(Collection::stream)
                                                                 .collect(Collectors.toSet());
    Set<String> result = new HashSet<>(allBlacklistProperties);
    result.removeAll(notBlacklistProperties);
    return result;
  }

  public boolean hasPermission(Authentication authentication, String projectPermission) {
    return hasAnyPermission(authentication, projectPermission);
  }

  public boolean hasAnyPermission(Authentication authentication, String... projectPermissions) {
    for (String projectPermission : projectPermissions) {
      //noinspection unchecked
      if (hasPermissionEnum(authentication, (T) Enum.valueOf(enumClass, projectPermission))) return true;
    }
    return false;
  }

  public boolean hasPermissionEnum(Authentication authentication, T projectPermission) {
    return hasAnyPermissionEnum(authentication, projectPermission);
  }

  public boolean hasAnyPermissionEnum(Authentication authentication, T... projectPermissions) {
    for (T projectPermission : projectPermissions) {
      if (hasPermission(getRoles(authentication), projectPermission)) return true;
    }
    return false;
  }

  public Set<T> getPermissions(Authentication authentication) {
    Set<String> roles = getRoles(authentication);
    return Arrays.stream(enumClass.getEnumConstants())
                 .filter(permission -> hasPermission(roles, permission))
                 .collect(Collectors.toSet());
  }

  public Set<String> getRoles(Authentication authentication) {
    return null == authentication ? Collections.emptySet() : authentication.getAuthorities()
                                                                           .stream()
                                                                           .map(GrantedAuthority::getAuthority)
                                                                           .map(value -> StringUtils.removeStartIgnoreCase(value, "role_"))
                                                                           .collect(Collectors.toSet());
  }

  private boolean hasPermission(Set<String> roles, T projectPermission) {
    if (defaultPermissions.contains(projectPermission)) return true;
    for (String role : roles) {
      Set<T> projectPermissions = roleName2permissions.get(StringUtils.lowerCase(role));
      if (null == projectPermissions) continue;
      if (projectPermissions.contains(projectPermission)) return true;
    }
    return false;
  }

}

