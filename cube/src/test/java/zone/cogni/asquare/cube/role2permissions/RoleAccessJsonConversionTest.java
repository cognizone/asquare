package zone.cogni.asquare.cube.role2permissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {RoleAccessJsonConversionTestConfig.class})
public class RoleAccessJsonConversionTest {

  @Autowired
  RoleAccessJsonConversion roleJsonToPermissions;

  @Test
  public void test_fetched_permissions_for_role() {
    //when
    Set<String> adminPermissions = roleJsonToPermissions.rolesToPermissions(Collections.singleton("ADMIN"));
    Set<String> viewpermissions = roleJsonToPermissions.rolesToPermissions(Collections.singleton("VIEW"));
    //then
    assertThat(adminPermissions).contains("search/filter_public/can-view", "search/filter_private/can-view");

    assertThat(viewpermissions).contains("search/filter_public/can-view");
    assertThat(viewpermissions).doesNotContain("search/filter_private/can-view");
  }

  @Test
  public void test_factor_authorities_are_not_treated_as_roles() {
    //given - Spring Security 7 adds a factor authority next to the roles after login
    Authentication passwordLogin = authentication(new SimpleGrantedAuthority("ADMIN"),
                                                  FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY));
    Authentication samlLogin = authentication(new SimpleGrantedAuthority("VIEW"),
                                              FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.SAML_RESPONSE_AUTHORITY));
    //when
    Set<String> passwordPermissions = roleJsonToPermissions.getPermissions(passwordLogin);
    Set<String> samlPermissions = roleJsonToPermissions.getPermissions(samlLogin);
    //then
    assertThat(passwordPermissions).isEqualTo(roleJsonToPermissions.rolesToPermissions(Set.of("ADMIN")));
    assertThat(samlPermissions).isEqualTo(roleJsonToPermissions.rolesToPermissions(Set.of("VIEW")));
  }

  @Test
  public void test_factor_only_authentication_has_no_permissions() {
    //given
    Authentication authentication = authentication(FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY));
    //when
    Set<String> permissions = roleJsonToPermissions.getPermissions(authentication);
    //then
    assertThat(permissions).isEmpty();
  }

  @Test
  public void test_undeclared_roles_are_skipped() {
    //given
    Authentication authentication = authentication(new SimpleGrantedAuthority("VIEW"),
                                                   new SimpleGrantedAuthority("ROLE_UNKNOWN"));
    //when
    Set<String> permissions = roleJsonToPermissions.getPermissions(authentication);
    Set<String> unknownOnly = roleJsonToPermissions.rolesToPermissions(Set.of("UNKNOWN"));
    //then
    assertThat(permissions).isEqualTo(roleJsonToPermissions.rolesToPermissions(Set.of("VIEW")));
    assertThat(unknownOnly).isEmpty();
  }

  @Test
  public void test_role_prefixed_and_lower_case_authorities_map_to_roles() {
    //given
    Authentication prefixed = new TestingAuthenticationToken("user", "password", "ROLE_ADMIN");
    Authentication lowerCase = new TestingAuthenticationToken("user", "password", "view");
    Authentication prefixedLowerCase = new TestingAuthenticationToken("user", "password", "ROLE_admin");
    //when - then
    assertThat(roleJsonToPermissions.getPermissions(prefixed))
            .containsExactlyInAnyOrder("search/filter_public/can-view", "search/filter_private/can-view");
    assertThat(roleJsonToPermissions.getPermissions(lowerCase))
            .containsExactly("search/filter_public/can-view");
    assertThat(roleJsonToPermissions.getPermissions(prefixedLowerCase))
            .containsExactlyInAnyOrder("search/filter_public/can-view", "search/filter_private/can-view");
  }

  private static Authentication authentication(GrantedAuthority... authorities) {
    return UsernamePasswordAuthenticationToken.authenticated("user", "password", List.of(authorities));
  }

}
