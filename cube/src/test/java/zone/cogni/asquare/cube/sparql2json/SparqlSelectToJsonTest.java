package zone.cogni.asquare.cube.sparql2json;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.commons.util.Preconditions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import zone.cogni.asquare.cube.util.TimingUtil;
import zone.cogni.sem.jena.JenaUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {SparqlSelectToJsonTestConfig.class})
class SparqlSelectToJsonTest {

  @Autowired
  SparqlSelectToJson selectToJson;

  @Test
  public void test_facets() {
    // given
    Model model = JenaUtils.read(new ClassPathResource("sparql2json/model.ttl"));

    // when
    ObjectNode convert = selectToJson.convert(model, Collections.emptyMap());

    // then
    assertTrue(convert.has("hasDates"));
    assertThat(convert.get("hasDates").size()).isEqualTo(3);

    assertTrue(convert.has("spouseName"));
    assertThat(convert.get("spouseName").asText()).isEqualTo("Marge Simpson");
  }

}