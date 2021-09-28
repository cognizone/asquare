package zone.cogni.asquare.access.simplerdf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import zone.cogni.asquare.access.ApplicationView;
import zone.cogni.asquare.access.ApplicationViewFactory;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.rdf.BasicRdfValue;
import zone.cogni.asquare.rdf.TypedResource;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static zone.cogni.asquare.rdf.TypedResourceBuilder.type;
import static zone.cogni.asquare.rdf.TypedResourceBuilder.uri;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = ApplicationViewFactory.class)
class SimpleRdfAccessServiceTypesTest {

  private static final String folder = "zone/cogni/asquare/access/simplerdf/";

  @Autowired
  ApplicationViewFactory applicationViewFactory;

  private ApplicationView view;

  @BeforeEach
  void beforeEach() {
    view = applicationViewFactory.getRdfView(() -> getResource("simple-rdf-types.ap.json"),
                                             () -> getResource("simple-rdf-types.ttl"));
  }

  @Test
  @Disabled
  void filter_by_rdf_type_and_in_scheme() {
    // given
    TypedResource genderConceptScheme = genderConceptScheme();

    // when
    List<BasicRdfValue> name = genderConceptScheme.getValues("name");
    List<TypedResource> concepts = genderConceptScheme.getValues("concepts");

    // then
    assertThat(name.size()).isEqualTo(1);
    assertThat(concepts.size()).isEqualTo(2);
  }

  private TypedResource genderConceptScheme() {
    return getTypedResource(type(view, "GenderConceptScheme"),
                            uri("http://example.cogni.zone/data/gender"));
  }

  private TypedResource getTypedResource(Supplier<ApplicationProfile.Type> type, Supplier<String> uri) {
    return view.getRepository().getTypedResource(type, uri);
  }

  private ClassPathResource getResource(String file) {
    return new ClassPathResource(folder + file);
  }
}
