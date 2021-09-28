package zone.cogni.asquare.applicationprofile.shacl;

import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import zone.cogni.asquare.applicationprofile.json.ApplicationProfileDeserializer;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcConfiguration;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcService;

public class ShaclDemo {

  public static void main(String[] args) throws InterruptedException {

    ClassPathResource resource = new ClassPathResource("owl/jolux.2.0.19.json");
    ApplicationProfile applicationProfile = new ApplicationProfileDeserializer(new PrefixCcService(new PrefixCcConfiguration())).apply(resource);

    Model model = new ApplicationProfile2ShaclModel().apply(applicationProfile);
    model.write(System.out, "TTL");

    Thread.sleep(300);
  }
}
