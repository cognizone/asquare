package zone.cogni.asquare.cube.convertor.inherit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import zone.cogni.asquare.applicationprofile.json.ApplicationProfileConfig;

@Configuration
@Import(ApplicationProfileConfig.class)
public class InheritApplicationProfileTestConfig {

  private final Resource simpleApplicationProfileResource;
  private final Resource inheritApplicationProfileResource;
  private final Resource inheritWrongApplicationProfileResource;

  public InheritApplicationProfileTestConfig(
    @Value("classpath:convertor/application-profile/simple-application-profile.json") Resource simpleApplicationProfileResource,
    @Value("classpath:convertor/application-profile/inherit-application-profile.json") Resource inheritApplicationProfileResource,
    @Value("classpath:convertor/application-profile/inherit-problem-application-profile.json") Resource inheritWrongApplicationProfileResource
  ) {
    this.simpleApplicationProfileResource = simpleApplicationProfileResource;
    this.inheritApplicationProfileResource = inheritApplicationProfileResource;
    this.inheritWrongApplicationProfileResource = inheritWrongApplicationProfileResource;
  }

  @Bean
  public Resource simpleApplicationProfileResource() {
    return simpleApplicationProfileResource;
  }

  @Bean
  public Resource inheritApplicationProfileResource() {
    return inheritApplicationProfileResource;
  }

  @Bean
  public Resource inheritWrongApplicationProfileResource() {
    return inheritWrongApplicationProfileResource;
  }

}
