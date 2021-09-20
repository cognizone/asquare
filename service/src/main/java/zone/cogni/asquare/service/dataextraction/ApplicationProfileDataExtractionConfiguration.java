package zone.cogni.asquare.service.dataextraction;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import zone.cogni.asquare.access.AccessService;
import zone.cogni.asquare.access.simplerdf.SimpleRdfAccessService;
import zone.cogni.asquare.applicationprofile.json.ApplicationProfileConfig;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcService;
import zone.cogni.asquare.triplestore.RdfStoreService;

import java.util.function.Supplier;

@Configuration
@Import({ApplicationProfileConfig.class, PrefixCcService.class})
public class ApplicationProfileDataExtractionConfiguration {

  private PrefixCcService prefixCcService;

  public ApplicationProfileDataExtractionConfiguration(PrefixCcService prefixCcService) {
    this.prefixCcService = prefixCcService;
  }

  @Bean
  public ApplicationProfileDataExtractionService getApplicationProfileDataExtractionService() {
    return new ApplicationProfileDataExtractionService(this);
  }

  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public AccessService getAccessService(Supplier<RdfStoreService> rdfStoreServiceSupplier) {
    return new SimpleRdfAccessService(prefixCcService, rdfStoreServiceSupplier);
  }

}
