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
import zone.cogni.asquare.rdf.TypedResource;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static zone.cogni.asquare.rdf.TypedResourceBuilder.type;
import static zone.cogni.asquare.rdf.TypedResourceBuilder.uri;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = ApplicationViewFactory.class)
class SimpleRdfAccessServicePropertiesTest {

  private static final String folder = "zone/cogni/asquare/access/simplerdf/";

  @Autowired
  ApplicationViewFactory applicationViewFactory;

  private ApplicationView view;

  @BeforeEach
  void beforeEach() {
    view = applicationViewFactory.getRdfView(() -> getResource("simple-rdf-properties.ap.json"),
                                             () -> getResource("simple-rdf-properties.ttl"));
  }

  @Test
  void property_no_range() {
    // given
    TypedResource jan = jan();

    // when
    List<TypedResource> relateds = jan.getValues("related");

    // then
    assertThat(relateds.size()).isEqualTo(6);
  }

  @Test
  void property_datatype_range_string() {
    // given
    TypedResource jan = jan();

    // when
    List<TypedResource> result = jan.getValues("stringRelated");

    // then
    assertThat(result.size()).isEqualTo(1);
  }


  @Test
  void property_datatype_range_int() {
    // given
    TypedResource jan = jan();

    // when
    List<TypedResource> result = jan.getValues("intRelated");

    // then
    assertThat(result.size()).isEqualTo(2);
  }

  @Test
  void property_class_id_range() {
    // given
    TypedResource jan = jan();

    // when
    List<TypedResource> relatedConcepts = jan.getValues("relatedConcept");

    // then
    assertThat(relatedConcepts.size()).isEqualTo(2);
  }

  @Test
  void property_class_id_range_without_data() {
    // given
    TypedResource jan = jan();

    // when
    List<TypedResource> unrelatedConcepts = jan.getValues("unrelatedConcept");

    // then
    assertThat(unrelatedConcepts.size()).isEqualTo(0);
  }

  @Test
  void property_class_id_and_in_scheme_range() {
    // given
    TypedResource jan = jan();

    // when
    List<TypedResource> sex = jan.getValues("gender");

    // then
    assertThat(sex.size()).isEqualTo(1);
  }

  @Test
  @Disabled
  // TODO fix it!
  void property_class_id_or_datatype_range() {
    // given
    TypedResource jan = jan();

    // when
    List<TypedResource> result = jan.getValues("orRelated");

    // then
    assertThat(result.size()).isEqualTo(3);
  }

  private TypedResource jan() {
    return getTypedResource(type(view, "Person"),
                            uri("http://example.cogni.zone/data/jan"));
  }

  private TypedResource getTypedResource(Supplier<ApplicationProfile.Type> type, Supplier<String> uri) {
    return view.getRepository().getTypedResource(type, uri);
  }

  private ClassPathResource getResource(String file) {
    return new ClassPathResource(folder + file);
  }

}
