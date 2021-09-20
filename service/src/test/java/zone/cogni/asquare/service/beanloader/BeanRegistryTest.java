package zone.cogni.asquare.service.beanloader;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import zone.cogni.asquare.access.AccessService;
import zone.cogni.asquare.access.ApplicationView;
import zone.cogni.asquare.access.ResourceSupplier;
import zone.cogni.asquare.access.simplerdf.SimpleRdfAccessService;
import zone.cogni.asquare.applicationprofile.json.ApplicationProfileConfig;
import zone.cogni.asquare.applicationprofile.json.ApplicationProfileSupplier;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.triplestore.jenamemory.InternalRdfStoreService;
import zone.cogni.asquare.triplestore.jenamemory.JenaModel;
import zone.cogni.asquare.triplestore.sparqlendpoint.SparqlEndpointDatabase;
import zone.cogni.sem.jena.JenaUtils;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
//@Import({BeanRegistryTest.Config.class, BeanLoaderService.class})
@Disabled
public class BeanRegistryTest {

//  @Autowired
//  Config config;
//
//  @Autowired
//  BeanLoaderService beanLoaderService;
//
//  @Test
//  void test_sparqlendpoint_database_1() {
//    Object sparqlEndpointDatabase = getBean("SparqlEndpointDatabase",
//                                            "http://test.org/resource/sparql-endpoint-database/1");
//
//    assertNotNull(sparqlEndpointDatabase);
//    assertSame(SparqlEndpointDatabase.class, sparqlEndpointDatabase.getClass());
//  }
//
//  @Test
//  void test_sparqlendpoint_database_2() {
//    Object sparqlEndpointDatabase = getBean("SparqlEndpointDatabase",
//                                            "http://test.org/resource/sparql-endpoint-database/2");
//
//    assertNotNull(sparqlEndpointDatabase);
//    assertSame(SparqlEndpointDatabase.class, sparqlEndpointDatabase.getClass());
//
//    SparqlEndpointDatabase instance = (SparqlEndpointDatabase) sparqlEndpointDatabase;
//    assertEquals("http://demo.com", instance.getResource());
//  }
//
//  @Test
//  void test_jena_model_1() {
//    Object jenaModel = getBean("JenaModel",
//                               "http://test.org/resource/jena-model/1");
//
//    assertNotNull(jenaModel);
//    assertSame(JenaModel.class, jenaModel.getClass());
//
//    JenaModel model = (JenaModel) jenaModel;
//    assertTrue(model.get().size() > 10);
//    assertTrue(model.get().size() < 1000);
//  }
//
//  @Test
//  void test_in_memory_database_1() {
//    Object inMemoryDatabase = getBean("InMemoryDatabase",
//                                      "http://test.org/resource/in-memory-database/1");
//
//    assertNotNull(inMemoryDatabase);
//  }
//
//  @Test
//  void test_rdf_repository_1() {
//    Object rdfRepository = getBean("RdfRepository",
//                                   "http://test.org/resource/rdf-repository/1");
//
//    assertNotNull(rdfRepository);
//    assertSame(SimpleRdfAccessService.class, rdfRepository.getClass());
//
//    AccessService instance = (AccessService) rdfRepository;
//    assertNotNull(instance.getRdfStoreService());
//  }
//
//  @Test
//  void test_rdf_repository_2() {
//    Object rdfRepository = getBean("RdfRepository",
//                                   "http://test.org/resource/rdf-repository/2");
//
//    assertNotNull(rdfRepository);
//    assertSame(SimpleRdfAccessService.class, rdfRepository.getClass());
//
//    AccessService instance = (AccessService) rdfRepository;
//    assertNotNull(instance.getRdfStoreService());
//  }
//
//  @Test
//  void test_resource_supplier_1() {
//    Object resourceSupplier = getBean("ResourceSupplier",
//                                      "http://test.org/resource/resource-supplier/1");
//
//    assertNotNull(resourceSupplier);
//    assertSame(ResourceSupplier.class, resourceSupplier.getClass());
//
//    ResourceSupplier instance = (ResourceSupplier) resourceSupplier;
//    assertSame(ClassPathResource.class, instance.get().getClass());
//    assertEquals("data-extraction-test.ap.json", instance.get().getFilename());
//  }
//
//  @Test
//  void test_application_profile_1() {
//    Object applicationProfile = getBean("ApplicationProfile",
//                                        "http://test.org/resource/application-profile/1");
//
//    assertNotNull(applicationProfile);
//    assertSame(ApplicationProfileSupplier.class, applicationProfile.getClass());
//
//    ApplicationProfileSupplier instance = (ApplicationProfileSupplier) applicationProfile;
//    assertNotNull(instance.getResource());
//    assertNotNull(instance.getResource().get().getFilename());
//    assertEquals("data-extraction-test.ap.json", instance.getResource().get().getFilename());
//  }
//
//  @Test
//  void test_application_view_1() {
//    Object applicationView = getBean("ApplicationView",
//                                     "http://test.org/resource/application-view/1");
//
//    assertNotNull(applicationView);
//    assertSame(ApplicationView.class, applicationView.getClass());
//
//    ApplicationView instance = (ApplicationView) applicationView;
//    assertNotNull(instance.getRepository());
//    assertNotNull(instance.getRepository().getRdfStoreService());
//    assertNotNull(instance.getApplicationProfile());
//  }
//
//  private Object getBean(String type, String uri) {
//    return beanLoaderService.get(config.getApplicationView(), type, uri);
//  }
//
//  @Import(ApplicationProfileConfig.class)
//  public static class Config {
//
//    private ApplicationProfileConfig applicationProfileConfig;
//
//    public Config(ApplicationProfileConfig applicationProfileConfig) {
//      this.applicationProfileConfig = applicationProfileConfig;
//    }
//
////    @Bean
////    public BeanLoaderService getBeanLoaderService() {
////      return new BeanLoaderService();
////    }
//
//    @Bean
//    public ApplicationView getApplicationView() {
//
//      return new ApplicationView(getAccessService(), getApplicationProfile());
//    }
//
//    @Bean
//    public AccessService getAccessService() {
//      RdfStoreService rdfStoreService = new InternalRdfStoreService(JenaUtils.read(new ClassPathResource("beanloader/bean-registry-test.ttl")));
//      return new SimpleRdfAccessService(prefixCcService, () -> rdfStoreService);
//    }
//
//    @Bean
//    public ApplicationProfile getApplicationProfile() {
//      return applicationProfileConfig.getDeserializer()
//              .apply(new ClassPathResource("application-profile/beans.ap.json"));
//    }
//
//  }
}
