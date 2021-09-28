package zone.cogni.asquare.cube.role2permissions;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
public class RoleAccessJsonConversionTestConfig {

  @Bean
  public RoleAccessJsonConversion roleJsonToPermissions() {
    Resource roleJson = new ClassPathResource("role2permissions/role_access.json");
    Set<String> operationIds = Stream.of("search/filter_public/can-view", "search/filter_private/can-view").collect(Collectors.toSet());
    return new RoleAccessJsonConversion(roleJson, operationIds);
  }
}
