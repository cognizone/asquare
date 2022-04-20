package zone.cogni.asquare.service.dataextraction;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import zone.cogni.asquare.access.AccessService;
import zone.cogni.asquare.access.simplerdf.SimpleRdfAccessService;
import zone.cogni.asquare.applicationprofile.json.ApplicationProfileConfig;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcService;
import zone.cogni.asquare.rdf.TypedResource;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.triplestore.jenamemory.InternalRdfStoreService;
import zone.cogni.core.util.function.CachingSupplier;
import zone.cogni.sem.jena.JenaUtils;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(classes = ApplicationProfileDataExtractionService.class)
@Import({ApplicationProfileDataExtractionConfiguration.class, PrefixCcService.class})
public class ApDataExtractionUnitTest {
  //todo : this should probably be renamed to ApplicationProfileDataExtractionServiceTest and remove the Spring Boot test already in
  @Autowired
  ApplicationProfileDataExtractionConfiguration config;

  @Autowired
  PrefixCcService prefixCcService;

  @Autowired
  ApplicationProfileConfig applicationProfileConfig;

  private Model targetModel;
  private Model sourceModel;
  private AccessService accessService;
  private ApplicationProfile applicationProfile;

  @BeforeEach
  public void loadModel() {
    ClassPathResource jsonResource = new ClassPathResource("jsonconversion/to-json-test.ap.json");
    applicationProfile = applicationProfileConfig.getDeserializer().apply(jsonResource);
    ApplicationProfileDataExtractionService apDataExtractionService = config.getApplicationProfileDataExtractionService();

    Supplier<RdfStoreService> rdfStoreService = getRdfStoreService();
    targetModel = apDataExtractionService.extractApplicationProfileData(applicationProfile, rdfStoreService.get());
    accessService = new SimpleRdfAccessService(prefixCcService, rdfStoreService);
  }

  @Test
  public void testModelNotEmpty() {
    assertFalse(targetModel.isEmpty());
  }

  @Test
  public void testModelCorrectSize() {
    assertThat(targetModel.size() <= sourceModel.size()).isTrue();
    assertThat(sourceModel.size()).isEqualTo(39);
    assertThat(targetModel.size()).isEqualTo(25);
  }

  @Test
  public void testModelNotClosed() {
    assertThat(targetModel.isClosed()).isFalse();
  }

  @Test
  public void testModelContents() {
    assertThat(targetModel.containsAny(sourceModel)).isTrue();

    //Using RDF For Testing
    Property sourceProperty = sourceModel.getProperty("http://purl.org/dc/terms/", "subject");
    RDFNode sourceNode = sourceModel.getResource("http://dbpedia.org/resource/Czech_Republic").getProperty(sourceProperty).getObject();
    assertThat(sourceNode).isNotNull();

    Property targetProperty = targetModel.getProperty("http://purl.org/dc/terms/", "subject");
    Statement targetStatement = targetModel.getResource("http://dbpedia.org/resource/Czech_Republic").getProperty(targetProperty);
    assertThat(targetStatement).isNull();

    targetProperty = targetModel.getProperty("http://www.w3.org/2000/01/", "rdf-schema#label");
    targetStatement = targetModel.getResource("http://dbpedia.org/resource/Czech_Republic").getProperty(targetProperty);
    assertThat(targetStatement).isNotNull();

//todo Tests are failing due to the ě,č characters of the uri (Němčice)
    targetProperty = targetModel.getProperty("http://dbpedia.org/ontology/", "demographicsAsOf");
    targetStatement = targetModel.getResource("http://dbpedia.org/resource/Němčice_(Prachatice_District)").getProperty(targetProperty);
    assertThat(targetStatement).isNotNull();

    targetProperty = targetModel.getProperty("http://dbpedia.org/ontology/", "demographicsAsOf");
    targetStatement = targetModel.getResource("http://dbpedia.org/resource/Němčice_(Prachatice_District)").getProperty(targetProperty);
    assertThat(targetStatement).isNotNull();

    //Using Typed Resource For Testing
    TypedResource country = accessService.getTypedResource(applicationProfile.getType("Country"),
                                                           ResourceFactory.createResource("http://dbpedia.org/resource/Czech_Republic"));
    assertThat(country.getValues("label")).isNotEmpty();

//todo Tests are failing due to the ě,č characters of the uri (Němčice)
    TypedResource settlement = accessService.getTypedResource(applicationProfile.getType("Settlement"),
                                                              ResourceFactory.createResource("http://dbpedia.org/resource/Němčice_(Prachatice_District)"));

    assertThat(settlement.getValues("name")).isNotEmpty();
    assertThat(settlement.getValues("abstract")).isNotEmpty();
    assertThat(settlement.getValues("populationTotal")).isNotEmpty();

    assertThat(settlement.getValues("populationTotal")).hasSize(1);
    assertThat(settlement.getValues("populationTotal").get(0).getLiteral().getInt()).isEqualTo(187);
    assertThat(settlement.getValues("demographicsAsOf")).isNotEmpty();
    assertThat(settlement.getValues("demographicsAsOf")).hasSize(1);
  }

  private Supplier<RdfStoreService> getRdfStoreService(){
    return CachingSupplier.memoize( () -> {
      Resource resource = new ClassPathResource("jsonconversion/to-json-test.data.ttl");
      sourceModel = JenaUtils.read(resource);
      return new InternalRdfStoreService(sourceModel);
    });
  }
}
