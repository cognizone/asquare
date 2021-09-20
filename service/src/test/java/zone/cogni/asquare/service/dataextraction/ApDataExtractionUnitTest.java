package zone.cogni.asquare.service.dataextraction;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit4.SpringRunner;
import zone.cogni.asquare.access.AccessService;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcService;
import zone.cogni.asquare.rdf.TypedResource;
import zone.cogni.asquare.access.simplerdf.SimpleRdfAccessService;
import zone.cogni.asquare.applicationprofile.json.ApplicationProfileConfig;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.triplestore.jenamemory.InternalRdfStoreService;
import zone.cogni.core.util.function.CachingSupplier;
import zone.cogni.sem.jena.JenaUtils;

import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
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

  @Before
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
    assertTrue("Target Model size could not exceed that of the Source",
               targetModel.size() <= sourceModel.size());
    assertEquals(39, sourceModel.size());
    assertEquals(25, targetModel.size());
  }

  @Test
  public void testModelNotClosed() {
    assertEquals(false, targetModel.isClosed());
  }

  @Test
  public void testModelContents() {
    assertTrue(targetModel.containsAny(sourceModel));

    //Using RDF For Testing
    Property sourceProperty = sourceModel.getProperty("http://purl.org/dc/terms/", "subject");
    RDFNode sourceNode = sourceModel.getResource("http://dbpedia.org/resource/Czech_Republic").getProperty(sourceProperty).getObject();
    assertNotNull(sourceNode);

    Property targetProperty = targetModel.getProperty("http://purl.org/dc/terms/", "subject");
    Statement targetStatement = targetModel.getResource("http://dbpedia.org/resource/Czech_Republic").getProperty(targetProperty);
    assertNull(targetStatement);

    targetProperty = targetModel.getProperty("http://www.w3.org/2000/01/", "rdf-schema#label");
    targetStatement = targetModel.getResource("http://dbpedia.org/resource/Czech_Republic").getProperty(targetProperty);
    assertNotNull(targetStatement);

//todo Tests are failing due to the ě,č characters of the uri (Němčice)
    targetProperty = targetModel.getProperty("http://dbpedia.org/ontology/", "demographicsAsOf");
    targetStatement = targetModel.getResource("http://dbpedia.org/resource/Němčice_(Prachatice_District)").getProperty(targetProperty);
    assertNotNull(targetStatement);

    targetProperty = targetModel.getProperty("http://dbpedia.org/ontology/", "demographicsAsOf");
    targetStatement = targetModel.getResource("http://dbpedia.org/resource/Němčice_(Prachatice_District)").getProperty(targetProperty);
    assertNotNull(targetStatement);

    //Using Typed Resource For Testing
    TypedResource country = accessService.getTypedResource(applicationProfile.getType("Country"),
                                                           ResourceFactory.createResource("http://dbpedia.org/resource/Czech_Republic"));
    assertTrue(!country.getValues("label").isEmpty());

//todo Tests are failing due to the ě,č characters of the uri (Němčice)
    TypedResource settlement = accessService.getTypedResource(applicationProfile.getType("Settlement"),
                                                              ResourceFactory.createResource("http://dbpedia.org/resource/Němčice_(Prachatice_District)"));

    assertFalse(settlement.getValues("name").isEmpty());
    assertFalse(settlement.getValues("abstract").isEmpty());
    assertFalse(settlement.getValues("populationTotal").isEmpty());
    assertEquals(1, settlement.getValues("populationTotal").size());
    assertEquals(187, settlement.getValues("populationTotal").get(0).getLiteral().getInt());
    assertFalse(settlement.getValues("demographicsAsOf").isEmpty());
    assertEquals(1, settlement.getValues("demographicsAsOf").size());
  }

  private Supplier<RdfStoreService> getRdfStoreService(){
    return CachingSupplier.memoize( () -> {
      Resource resource = new ClassPathResource("jsonconversion/to-json-test.data.ttl");
      sourceModel = JenaUtils.read(resource);
      return new InternalRdfStoreService(sourceModel);
    });
  }
}
