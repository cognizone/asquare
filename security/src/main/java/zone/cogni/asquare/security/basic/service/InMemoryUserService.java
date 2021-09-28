package zone.cogni.asquare.security.basic.service;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import zone.cogni.asquare.security.basic.model.UserInfo;
import zone.cogni.asquare.security.model.AuthenticationToken;
import zone.cogni.asquare.security.service.PermissionService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class InMemoryUserService implements UserService {

  private static final Logger log = LoggerFactory.getLogger(InMemoryUserService.class);

  private final PermissionService<?> permissionService;

  public InMemoryUserService(PermissionService permissionService) {
    this.permissionService = permissionService;
  }

  public UserInfo getUserInfo(String userId) {
    for (String oneRole : permissionService.getRoleNames()) {
      if (oneRole.equalsIgnoreCase(userId)) {
        UserInfo userInfo = createFromRole(oneRole);
        AuthenticationToken authenticationToken = userInfo.convertToAuthenticationToken();
        log.info("Loading info for user {}", authenticationToken);
        return userInfo;
      }
    }
    return null;
  }

  private UserInfo createFromRole(String role) {
    String ldapGroup = "MAE";
    if (role.equalsIgnoreCase("VIEW")) {
      ldapGroup = null;
    }
    return new UserInfo(role,
                        "full" + role + "name",
                        ldapGroup,
                        "luxMailServiceTester@gmail.com",
                        "+32123456789",
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));
  }

  public List<AuthenticationToken> findUsers(String name) {
    List<AuthenticationToken> result = new ArrayList<>();
    for (String oneRole : permissionService.getRoleNames()) {
      if (StringUtils.containsIgnoreCase("full" + oneRole + "name", name)) {
        UserInfo userInfo = createFromRole(oneRole);
        result.add(userInfo.convertToAuthenticationToken());
      }
    }
    return result;
  }

  public List<AuthenticationToken> findUsers(String name, List<String> ldapGroups) {
    return findUsers(name).stream()
                          .filter(ldapUser -> ldapGroups.contains(ldapUser.getLdapGroup().orElse(null)))
                          .collect(Collectors.toList());
  }

  public void clearLdapLoginCache() {

  }
}
