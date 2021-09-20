package zone.cogni.asquare.security.permission;

import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.core.Authentication;
import zone.cogni.asquare.security.service.PermissionService;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

class PermissionAccessDecisionVoter<PERMISSION_TYPE extends Enum<PERMISSION_TYPE>> implements AccessDecisionVoter<Object> {
  private final PermissionService<PERMISSION_TYPE> permissionService;

  PermissionAccessDecisionVoter(PermissionService<PERMISSION_TYPE> permissionService) {
    this.permissionService = permissionService;
  }

  @Override
  public boolean supports(ConfigAttribute attribute) {
    return attribute instanceof PermissionConfigAttribute;
  }

  @Override
  public boolean supports(Class<?> clazz) {
    return true;
  }

  @Override
  public int vote(Authentication authentication, Object object, Collection<ConfigAttribute> attributes) {
    @SuppressWarnings("unchecked")
    List<PermissionConfigAttribute<PERMISSION_TYPE>> castedAttributes = attributes
            .stream()
            .filter(PermissionConfigAttribute.class::isInstance)
            .map(item -> (PermissionConfigAttribute<PERMISSION_TYPE>) item)
            .collect(Collectors.toList());
    return new Handler(authentication, castedAttributes).vote();
  }

  private class Handler implements PermissionStrategyVisitor<PERMISSION_TYPE[], Boolean> {
    private final Authentication authentication;
    private final List<PermissionConfigAttribute<PERMISSION_TYPE>> attributes;

    private Handler(Authentication authentication, List<PermissionConfigAttribute<PERMISSION_TYPE>> attributes) {
      this.authentication = authentication;
      this.attributes = attributes;
    }

    private int vote() {
      if (attributes.isEmpty()) return ACCESS_ABSTAIN;
      for (PermissionConfigAttribute<PERMISSION_TYPE> attribute : attributes) {
        PERMISSION_TYPE[] permissions = attribute.getPermissions();
        PermissionStrategy permissionStrategy = attribute.getPermissionStrategy();

        Boolean isAccepted = permissionStrategy.accept(this, permissions);
        if (isAccepted) return ACCESS_GRANTED;
      }
      return ACCESS_DENIED;
    }

    @Override
    public Boolean visitAll(PERMISSION_TYPE[] permissions) {
      return Arrays
              .stream(permissions)
              .allMatch(permission -> permissionService.hasAnyPermissionEnum(authentication, permission));
    }

    @Override
    public Boolean visitAny(PERMISSION_TYPE[] permissions) {
      return permissionService.hasAnyPermissionEnum(authentication, permissions);
    }
  }
}
