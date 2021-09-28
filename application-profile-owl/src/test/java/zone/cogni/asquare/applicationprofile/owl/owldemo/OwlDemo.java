package zone.cogni.asquare.applicationprofile.owl.owldemo;

import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import zone.cogni.asquare.applicationprofile.json.ApplicationProfileSerializer;
import zone.cogni.asquare.applicationprofile.model.basic.def.ApplicationProfileDef;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.Owl2SquareOwl;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.SquareOwl2ApplicationProfile;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.Owl2OwlRules;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.OwlRules;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.OwlRules2ApplicationProfile;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcService;
import zone.cogni.sem.jena.JenaUtils;


// IMPORTANT note to run
//    program argument --spring.main.web-application-type=none
//    active profile owl-demo

@SpringBootApplication
@Import(PrefixCcService.class)
public class OwlDemo implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(OwlDemo.class);

  private PrefixCcService prefixCcService;

  public OwlDemo(PrefixCcService prefixCcService) {
    this.prefixCcService = prefixCcService;
  }

  public static void main(String[] args) {
    SpringApplication.run(OwlDemo.class, args);
  }

  @Override
  public void run(String... args) throws Exception {
    //    Model model = JenaUtils.read(new ClassPathResource("owl-demo/milieuvocab.rdf"));
//    Model model = JenaUtils.read(new ClassPathResource("owl-demo/jolux.3.19.ttl"));
    Model model = JenaUtils.read(new ClassPathResource("owldemo/jolux.3.21.ttl"));

//    Model newModel = new ExpandOwlModel().apply(model);
//    newModel.write(System.out, "turtle");


//    Model model = JenaUtils.read(new ClassPathResource("owl-demo/jolux.light.ttl"));

//    Model model = JenaUtils.read(new ClassPathResource("owl-demo/jolux.3.0.9.ttl"));
//    Model model = JenaUtils.read(new ClassPathResource("owl-demo/jolux.3.0.8.ttl"));
//    Model model = JenaUtils.read(new ClassPathResource("owl-demo/jolux.2.0.19.ttl"));
//    Model model = JenaUtils.read(new ClassPathResource("owl-demo/prov-o.rdf"));

//    ClassIdProvider classIdProvider = new ClassIdProvider();
//    classIdProvider.setShortNameForNamespace("Cc","http://creativecommons.org/ns#");
//    classIdProvider.setShortNameForNamespace("Rov","http://www.w3.org/ns/regorg#");
//    classIdProvider.setShortNameForNamespace("Foaf","http://xmlns.com/foaf/0.1/");
//    classIdProvider.setShortNameForNamespace("Prov","http://www.w3.org/ns/prov#");
//    classIdProvider.setShortNameForNamespace("Void","http://rdfs.org/ns/void#");
//    classIdProvider.setShortNameForNamespace("Skos","http://www.w3.org/2004/02/skos/core#");
//    classIdProvider.setShortNameForNamespace("Dct","http://purl.org/dc/terms/");
//    classIdProvider.setShortNameForNamespace("Sdmx","http://purl.org/linked-data/sdmx#");

//    model.write(System.out, "turtle");


////    ApplicationProfileDef applicationProfile = readOwlIntoApplicationProfile().apply(model);
    OwlRules owlRules = new Owl2OwlRules().apply(model);
    owlRules.printSummary("Start");
////    System.out.println("owlRules = \n" + owlRules);
//
    ApplicationProfileDef applicationProfile = new OwlRules2ApplicationProfile().apply(owlRules);
    owlRules.printSummary("End");

    log.info("Model: \n{}", new ApplicationProfileSerializer().apply(applicationProfile));
//
    Thread.sleep(300);
  }

  private Owl2SquareOwl readOwlIntoOwlJson() {
    return new Owl2SquareOwl(prefixCcService);
  }

  private SquareOwl2ApplicationProfile readOwlIntoApplicationProfile() {
    return new SquareOwl2ApplicationProfile(prefixCcService);
  }


}
