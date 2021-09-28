package zone.cogni.asquare.cube.role2permissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;
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

}
