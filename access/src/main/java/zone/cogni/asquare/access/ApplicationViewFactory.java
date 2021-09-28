package zone.cogni.asquare.access;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.Resource;
import zone.cogni.asquare.access.simplerdf.SimpleRdfAccessService;
import zone.cogni.asquare.applicationprofile.json.ApplicationProfileConfig;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcService;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.triplestore.jenamemory.InMemoryDatabase;
import zone.cogni.asquare.triplestore.jenamemory.JenaModel;

import java.util.Arrays;
import java.util.function.Supplier;

@Configuration
@Import({PrefixCcService.class, ApplicationProfileConfig.class})
public class ApplicationViewFactory {

  private PrefixCcService prefixCcService;
  private ApplicationProfileConfig applicationProfileConfig;

  public ApplicationViewFactory(PrefixCcService prefixCcService,
                                ApplicationProfileConfig applicationProfileConfig) {
    this.prefixCcService = prefixCcService;
    this.applicationProfileConfig = applicationProfileConfig;
  }

  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public ApplicationView getRdfView(Supplier<Resource> applicationProfileSupplier,
                                    Supplier<Resource> dataSupplier) {
    RdfStoreService rdfStoreService = getRdfStoreService(dataSupplier.get());
    SimpleRdfAccessService accessService = new SimpleRdfAccessService(prefixCcService, () -> rdfStoreService);
    ApplicationProfile applicationProfile = applicationProfileConfig.getDeserializer()
                                                                    .apply(applicationProfileSupplier.get());
    return new ApplicationView(accessService, applicationProfile);
  }

  private static RdfStoreService getRdfStoreService(org.springframework.core.io.Resource... resources) {
    JenaModel jenaModel = new JenaModel();
    jenaModel.setResources(Arrays.asList(resources));

    RdfStoreService rdfStoreService = new InMemoryDatabase();
    ((InMemoryDatabase) rdfStoreService).setJenaModel(jenaModel);
    return rdfStoreService;
  }

}
