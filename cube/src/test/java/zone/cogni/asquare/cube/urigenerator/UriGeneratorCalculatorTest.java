package zone.cogni.asquare.cube.urigenerator;

import com.google.common.collect.ImmutableMap;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import zone.cogni.sem.jena.JenaUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = UriGeneratorCalculatorTestConfig.class)
public class UriGeneratorCalculatorTest {

  @Autowired
  UriGeneratorCalculator uriCalculator;

  @Test
  public void test_uris_converted() {
    //given
    Model model = JenaUtils.read(new ClassPathResource("urigenerator/model.ttl"));
    //when
    Model converted = uriCalculator.convert(model, ImmutableMap.of("baseUri", "http://asquare.cogni.zone"));
    //then
    assertThat(converted.containsResource(ResourceFactory.createResource("http://asquare.cogni.zone/5")));
    assertThat(converted.containsResource(ResourceFactory.createResource("http://asquare.cogni.zone/2021/0005")));
  }
}
