package zone.cogni.asquare.service.dataextraction;

import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import zone.cogni.asquare.applicationprofile.json.ApplicationProfileConfig;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.triplestore.sparqlendpoint.SparqlEndpointRdfStoreService;

import java.io.FileWriter;
import java.io.IOException;

@SpringBootApplication(scanBasePackageClasses = ApplicationProfileDataExtractionConfiguration.class)
@Import(ApplicationProfileDataExtractionServiceTest.Config.class)
public class ApplicationProfileDataExtractionServiceTest implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(ApplicationProfileDataExtractionServiceTest.class);

  public static void main(String[] args) {
    SpringApplication.run(ApplicationProfileDataExtractionServiceTest.class, args);
  }

  private final ApplicationProfileConfig applicationProfileConfig;
  private final ApplicationProfileDataExtractionService apDataExtractionService;
  private final RdfStoreService rdfStoreService;


  public ApplicationProfileDataExtractionServiceTest(ApplicationProfileConfig applicationProfileConfig,
                                                     RdfStoreService rdfStoreService,
                                                     ApplicationProfileDataExtractionService apDataExtractionService) {
    this.applicationProfileConfig = applicationProfileConfig;
    this.apDataExtractionService = apDataExtractionService;
    this.rdfStoreService = rdfStoreService;
  }

  @Override
  public void run(String... args) throws IOException {
    log.info("Loading JSON Application Profile");
    ClassPathResource jsonResource = new ClassPathResource("jsonconversion/to-json-test.ap.json");
    ApplicationProfile applicationProfile = applicationProfileConfig.getDeserializer().apply(jsonResource);
    log.info("Starting Data Extraction ");
    Model model = apDataExtractionService.extractApplicationProfileData(applicationProfile, rdfStoreService);
    log.info("Writing File ");
    model.write(new FileWriter("to-json-test-result.ttl"), "TURTLE");
    log.info("DONE");
  }


  @Configuration
  public static class Config {
    @Bean
    public RdfStoreService getRdfStoreService() {
      return new SparqlEndpointRdfStoreService("http://localhost:3030/jsontest/query");
    }

  }
}
