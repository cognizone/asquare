package zone.cogni.asquare.cube.role2permissions;

import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import zone.cogni.asquare.cube.util.TimingUtil;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;


public class RoleAccessJsonConversion {
  private static final Logger log = LoggerFactory.getLogger(RoleAccessJsonConversion.class);

  private final RoleAccessRoot roleAccessRoot;

  public RoleAccessJsonConversion(Resource roleJson, Set<String> operationIds) {
    this.roleAccessRoot = RoleAccessRoot.load(roleJson, operationIds);
  }

  public Supplier<Set<String>> getPermissionsSupplier(Authentication authentication) {
    return () -> getPermissions(authentication);
  }

  public Set<String> getPermissions(Authentication authentication) {
    Set<String> roles = getRoles(authentication);
    Set<String> permissions = rolesToPermissions(roles);

    if (log.isDebugEnabled()) {
      log.debug("Checking roles for user {} with roles: \n {} \n and permissions : \n {} ",
                authentication.getName(), String.join("\n", roles), String.join(",", permissions));
    }

    return permissions;
  }

  @Nonnull
  private Set<String> getRoles(Authentication authentication) {
    return authentication.getAuthorities().stream()
                         // Spring Security 7 authentication factors (FACTOR_PASSWORD, FACTOR_OTT, ...) are an auth-assurance
                         // signal, not application roles - exclude them here so they never reach the role lookup.
                         .filter(Predicate.not(FactorGrantedAuthority.class::isInstance))
                         .map(GrantedAuthority::getAuthority)
                         .map(this::authorityToRole)
                         .collect(Collectors.toSet());
  }

  //  making sure it works for e.g. SUPER_ADMIN and ROLE_SUPER_ADMIN)
  private String authorityToRole(String authority) {
    String withoutRole = StringUtils.remove(authority, "ROLE_");
    return StringUtils.upperCase(withoutRole);
  }

  public Set<String> rolesToPermissions(Set<String> roles) {
    long start = System.nanoTime();


    Set<String> permissions = roles.stream()
                                   .map(this::getDeclaredRole)
                                   .filter(Objects::nonNull)
                                   .map(Role::getRules)
                                   .flatMap(Collection::stream)
                                   .collect(Collectors.toSet());

    if (log.isDebugEnabled()) log.debug("calculated permissions in {} ms", TimingUtil.millisSinceStart(start, 3));
    return permissions;
  }

  // defensive backstop: anything reaching here that isn't a declared role is skipped
  // (factors are already excluded in getRoles) - logged so a real role typo stays visible.
  private Role getDeclaredRole(String role) {
    Role found = roleAccessRoot.getRoles().get(role);
    if (found == null) log.warn("Ignoring '{}' - not a declared role in the role-access config", role);
    return found;
  }
}