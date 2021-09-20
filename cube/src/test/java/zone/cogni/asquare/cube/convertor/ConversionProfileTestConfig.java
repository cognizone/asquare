package zone.cogni.asquare.cube.convertor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import zone.cogni.asquare.applicationprofile.json.ApplicationProfileConfig;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;

@Configuration
@Import(ApplicationProfileConfig.class)
public class ConversionProfileTestConfig {

  private final Resource draftApplicationProfileResource;
  private final ApplicationProfileConfig applicationProfileConfig;

  public ConversionProfileTestConfig(@Value("classpath:convertor/draft-application-profile.json") Resource draftApplicationProfileResource,
                                     ApplicationProfileConfig applicationProfileConfig) {
    this.draftApplicationProfileResource = draftApplicationProfileResource;
    this.applicationProfileConfig = applicationProfileConfig;
  }

  @Bean
  public ApplicationProfile draftApplicationProfile() {
    return applicationProfileConfig.getDeserializer().apply(draftApplicationProfileResource);
  }
}
