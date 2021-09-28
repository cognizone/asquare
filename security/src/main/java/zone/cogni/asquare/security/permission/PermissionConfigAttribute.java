package zone.cogni.asquare.security.permission;

import org.springframework.security.access.ConfigAttribute;

class PermissionConfigAttribute<PERMISSION_TYPE extends Enum> implements ConfigAttribute {
  private static final long serialVersionUID = 1L;
  private final PERMISSION_TYPE[] permissions;
  private final PermissionStrategy permissionStrategy;

  @SuppressWarnings("MethodCanBeVariableArityMethod")
  PermissionConfigAttribute(PERMISSION_TYPE[] permissions, PermissionStrategy permissionStrategy) {
    this.permissions = permissions;
    this.permissionStrategy = permissionStrategy;
  }

  public PERMISSION_TYPE[] getPermissions() {
    return permissions;
  }

  public PermissionStrategy getPermissionStrategy() {
    return permissionStrategy;
  }

  @Override
  public String getAttribute() {
    return null;
  }
}
