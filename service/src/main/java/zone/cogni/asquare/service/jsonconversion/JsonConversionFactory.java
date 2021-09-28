package zone.cogni.asquare.service.jsonconversion;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcService;

@Configuration
@Import(PrefixCcService.class)
public class JsonConversionFactory {

  private final PrefixCcService prefixCcService;

  public JsonConversionFactory(PrefixCcService prefixCcService) {
    this.prefixCcService = prefixCcService;
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public ApplicationViewToJson getApplicationViewToJson() {
    return new ApplicationViewToJson(prefixCcService);
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public TypedResourceToJson getTypedResourceToJson() {
    return new TypedResourceToJson(prefixCcService);
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public JsonToTypedResource getJsonToUpdatableResource() {
    return new JsonToTypedResource(prefixCcService);
  }



}
