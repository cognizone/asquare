package zone.cogni.asquare.access.simplerdf.snippet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import zone.cogni.asquare.access.ApplicationView;
import zone.cogni.asquare.access.ApplicationViewFactory;
import zone.cogni.asquare.rdf.TypedResource;

import static zone.cogni.asquare.rdf.TypedResourceBuilder.type;
import static zone.cogni.asquare.rdf.TypedResourceBuilder.uri;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = ApplicationViewFactory.class)
public class SnippetApplicationProfileTest {

  private static final String folder = "zone/cogni/asquare/access/simplerdf/snippet/";

  @Autowired
  ApplicationViewFactory applicationViewFactory;

  @Test
  void get_parents_of_pierre() {
    ApplicationView view = applicationViewFactory.getRdfView(() -> getResource("snippet-application-profile.ap.json"),
                                                             () -> getResource("snippet-application-profile-data.ttl"));

    TypedResource pierre = view.getRepository().getTypedResource(type(view, "Person"),
                                                                 uri("http://example.zone.cogni/data/pierre"));

//    List<RdfValue> parents = pierre.getValues("parent");
//    System.out.println("parents = " + parents);

  }

  private ClassPathResource getResource(String file) {
    return new ClassPathResource(folder + file);
  }
}
