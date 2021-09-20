package zone.cogni.asquare.cube.role2permissions;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import zone.cogni.asquare.cube.util.TimingUtil;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Set;
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
                                   .map(role -> roleAccessRoot.getRoles().get(role))
                                   .map(Role::getRules)
                                   .flatMap(Collection::stream)
                                   .collect(Collectors.toSet());

    if (log.isDebugEnabled()) log.debug("calculated permissions in {} ms", TimingUtil.millisSinceStart(start, 3));
    return permissions;
  }
}