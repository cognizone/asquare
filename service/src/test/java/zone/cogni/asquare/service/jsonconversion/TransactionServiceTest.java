package zone.cogni.asquare.service.jsonconversion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import zone.cogni.asquare.access.AccessService;
import zone.cogni.asquare.access.ApplicationView;
import zone.cogni.asquare.access.simplerdf.SimpleRdfAccessService;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcService;
import zone.cogni.asquare.edit.BasicRdfTransactionService;
import zone.cogni.asquare.edit.EditableAccessService;
import zone.cogni.asquare.edit.MutableResource;
import zone.cogni.asquare.rdf.TypedResource;
import zone.cogni.asquare.service.ApplicationViewTestConfig;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.triplestore.jenamemory.InternalRdfStoreService;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
//@SpringBootTest(classes = {TransactionServiceTest.Config.class, PrefixCcService.class})
@Import({TransactionServiceTest.Config.class, PrefixCcService.class})
public class TransactionServiceTest {

  @Autowired
  TransactionServiceTest.Config config;

  @Autowired
  PrefixCcService prefixCcService;

  @Test
  public void testBasicTransaction() {
    ApplicationView applicationView = config.getSettlementApplicationView();

    ObjectNode json = (ObjectNode) config.getJson("jsonconversion/settlement.json");
    List<MutableResource> jsonResource = config.getJsonToUpdatableResource()
        .withApplicationView(applicationView)
        .withJsonRoot(json).get();

    EditableAccessService accessService = new EditableAccessService(applicationView.getRepository());
    ApplicationProfile profile = applicationView.getApplicationProfile();

    List<? extends TypedResource> before = accessService.findAll(profile.getType("Settlement"));
    assertEquals(0, before.size());

    config.getTransactionService(accessService).update(jsonResource.stream()
        .map(accessService::getUpdatableResource).collect(Collectors.toList()));

    List<? extends TypedResource> added = accessService.findAll(profile.getType("Settlement"));

    assertEquals(1, added.size());
  }


  @Configuration
  @Import({ApplicationViewTestConfig.class, JsonConversionFactory.class, PrefixCcService.class})
  public static class Config {

    private final ApplicationViewTestConfig applicationViewTestConfig;
    private final JsonConversionFactory applicationViewToJsonFactory;
    private final RdfStoreService rdfStoreService;
    private final PrefixCcService prefixCcService;

    public Config(ApplicationViewTestConfig applicationViewTestConfig,
                  JsonConversionFactory applicationViewToJsonFactory,
                  PrefixCcService prefixCcService) {
      this.applicationViewTestConfig = applicationViewTestConfig;
      this.applicationViewToJsonFactory = applicationViewToJsonFactory;
      this.prefixCcService = prefixCcService;
      rdfStoreService = new InternalRdfStoreService();
    }

    private ApplicationView getSettlementApplicationView() {
      return new ApplicationView( new SimpleRdfAccessService(prefixCcService, this::getRdfStoreService),
          applicationViewTestConfig.getApplicationProfile( "jsonconversion/to-json-test.ap.json"));
    }

    public JsonToTypedResource getJsonToUpdatableResource () {
      return applicationViewToJsonFactory.getJsonToUpdatableResource();
    }

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public RdfStoreService getRdfStoreService() {
      return this.rdfStoreService;
    }


    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public BasicRdfTransactionService getTransactionService(AccessService accessService) {
      return new BasicRdfTransactionService(accessService.getRdfStoreService());
    }

    public JsonNode getJson(String path) {

      ClassPathResource jsonResource = new ClassPathResource(path);
      try {
        return new ObjectMapper().readTree(jsonResource.getInputStream());
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }

    }
  }
}
