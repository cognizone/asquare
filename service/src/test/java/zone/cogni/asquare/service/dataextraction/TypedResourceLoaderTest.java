package zone.cogni.asquare.service.dataextraction;

import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import zone.cogni.asquare.access.ApplicationView;
import zone.cogni.asquare.service.ApplicationViewTestConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static zone.cogni.asquare.service.dataextraction.TypedResourceLoader.typedResource;

@ExtendWith(SpringExtension.class)
@Import(TypedResourceLoaderTest.Config.class)
public class TypedResourceLoaderTest {

  @Autowired
  Config config;

  @Test
  void test_setup() {
    assertNotNull(config);
  }

  @Test
  void test_get_without_setup() {
    assertThrows(RuntimeException.class, () -> {
      new TypedResourceLoader()
              .get();
    });
  }

  @Test
  void test_get_nothing() {
    Model model = new TypedResourceLoader()
            .withView(() -> config.getTestApplicationView())
            .get();

    assertEquals(0, model.size());
  }

  @Test
  void test_get_country() {
    Model model = new TypedResourceLoader()
            .withView(() -> config.getTestApplicationView())
            .loading(typedResource("Country", "http://dbpedia.org/resource/Czech_Republic"))
            .get();

    assertEquals(13, model.size());
  }

  @Test
  void test_get_country_illegal_label() {
    assertThrows(RuntimeException.class, () -> {
      new TypedResourceLoader()
              .withView(() -> config.getTestApplicationView())
              .loading(
                      typedResource("Country", "http://dbpedia.org/resource/Czech_Republic")
                              .attributes("label2")
              ).get();
    });
  }

  @Test
  void test_get_country_label() {

    Model model = new TypedResourceLoader()
            .withView(() -> config.getTestApplicationView())
            .loading(
                    typedResource("Country", "http://dbpedia.org/resource/Czech_Republic")
                            .attributes("label")
            )
            .get();

    assertEquals(13, model.size());
  }

  // TODO think about what this should do ?!!
  @Test
  void test_get_wrong_type() {
    assertThrows(RuntimeException.class, () -> {
      Model model = new TypedResourceLoader()
              .withView(() -> config.getTestApplicationView())
              .loading(typedResource("Settlement", "http://dbpedia.org/resource/Czech_Republic"))
              .get();
    });
//    model.write(System.out, "N-triples");
//    assertEquals(0, model.size());
  }

  @Test
  void test_get_multiple_countries() {
    Model model = new TypedResourceLoader()
            .withView(() -> config.getTestApplicationView())
            .loading(typedResource("Country", "http://dbpedia.org/resource/Czech_Republic"))
            .loading(typedResource("Country", "http://dbpedia.org/resource/Zombie_Nation"))
            .get();

    assertEquals(26, model.size());
  }

  @Test
  void test_get_settlement() {
    Model model = new TypedResourceLoader()
            .withView(() -> config.getTestApplicationView())
            .loading(typedResource("Settlement", "http://dbpedia.org/resource/Němčice_(Prachatice_District)"))
            .get();

    assertEquals(13, model.size());

  }

  @Test
  void test_get_settlement_attributes() {
    Model model = new TypedResourceLoader()
            .withView(() -> config.getTestApplicationView())
            .loading(
                    typedResource("Settlement", "http://dbpedia.org/resource/Němčice_(Prachatice_District)")
                            .attributes("name", "abstract", "populationTotal")
            ).get();

    assertEquals(9, model.size());
  }

  @Test
  void test_get_settlement_path() {
    Model model = new TypedResourceLoader()
            .withView(() -> config.getTestApplicationView())
            .loading(
                    typedResource("Settlement", "http://dbpedia.org/resource/Němčice_(Prachatice_District)")
                            .paths("country")
            ).get();

    assertEquals(25, model.size());
  }

  @Test
  void test_get_settlement_attributes_and_path() {
    Model model = new TypedResourceLoader()
            .withView(() -> config.getTestApplicationView())
            .loading(
                    typedResource("Settlement", "http://dbpedia.org/resource/Němčice_(Prachatice_District)")
                            .attributes("name", "populationTotal")
                            .paths("country")
            ).get();

    assertEquals(16, model.size());
  }


  @Configuration
  @Import(ApplicationViewTestConfig.class)
  public static class Config {

    private final ApplicationViewTestConfig applicationViewTestConfig;

    public Config(ApplicationViewTestConfig applicationViewTestConfig) {
      this.applicationViewTestConfig = applicationViewTestConfig;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public ApplicationView getTestApplicationView() {
      return applicationViewTestConfig.getApplicationView("data-extraction/data-extraction-test.ap.json",
                                                          "data-extraction/data-extraction-test.data.ttl");
    }

  }


}

