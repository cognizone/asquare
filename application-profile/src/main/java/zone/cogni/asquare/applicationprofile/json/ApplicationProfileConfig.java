package zone.cogni.asquare.applicationprofile.json;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcService;

@Configuration
@Import(PrefixCcService.class)
public class ApplicationProfileConfig {

  private final PrefixCcService prefixCcService;

  public ApplicationProfileConfig(PrefixCcService prefixCcService) {
    this.prefixCcService = prefixCcService;
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public ApplicationProfileSerializer getSerializer() {
    return new ApplicationProfileSerializer();
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public ApplicationProfileDeserializer getDeserializer() {
    return new ApplicationProfileDeserializer(prefixCcService);
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public ApplicationProfileSimpleJson getSimpleJson() {
    return new ApplicationProfileSimpleJson();
  }


}
