package zone.cogni.asquare.service;

import org.apache.jena.rdf.model.Model;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import zone.cogni.asquare.access.ApplicationView;
import zone.cogni.asquare.access.simplerdf.SimpleRdfAccessService;
import zone.cogni.asquare.applicationprofile.json.ApplicationProfileConfig;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcService;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.triplestore.jenamemory.InternalRdfStoreService;
import zone.cogni.sem.jena.JenaUtils;

@Configuration
@Import({ApplicationProfileConfig.class, PrefixCcService.class})
public class ApplicationViewTestConfig {

  private final PrefixCcService prefixCcService;
  private final ApplicationProfileConfig applicationProfileConfig;

  public ApplicationViewTestConfig(PrefixCcService prefixCcService, ApplicationProfileConfig applicationProfileConfig) {
    this.prefixCcService = prefixCcService;
    this.applicationProfileConfig = applicationProfileConfig;
  }

  @SuppressWarnings({"SpringJavaInjectionPointsAutowiringInspection", "rawtypes", "unchecked"})
  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public ApplicationView getApplicationView(String applicationProfilePath, String databasePath) {
    return new ApplicationView(new SimpleRdfAccessService(prefixCcService, () -> getRdfStoreService(databasePath)),
                               getApplicationProfile(applicationProfilePath));
  }

//  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
//  @Bean
//  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
//  public ApplicationView getApplicationView (ApplicationProfile applicationProfile,
//                                             Supplier<RdfStoreService> rdfStoreServiceSupplier) {
//
//    return new ApplicationView(new SimpleRdfAccessService(rdfStoreServiceSupplier), applicationProfile);
//  }

  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public RdfStoreService getRdfStoreService(String databasePath) {
    Resource resource = new ClassPathResource(databasePath);
    Model model = JenaUtils.read(resource);
    return new InternalRdfStoreService(model);
  }

  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public ApplicationProfile getApplicationProfile(String applicationProfilePath) {
    ClassPathResource jsonResource = new ClassPathResource(applicationProfilePath);
    return applicationProfileConfig.getDeserializer().apply(jsonResource);
  }


}
