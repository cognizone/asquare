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
  public void demo() {
    List nullList = null;
    List emptyList = new ArrayList();
    List<String> nonEmpty = Arrays.asList("one", "two", "three");
    assertEmptyList(null);

    int size = 10000;
    System.out.println("list is null");
    runManyTimes(size, "if test", () -> runIfTest(nullList));
    runManyTimes(size, "assert ", () -> assertEmptyList(nullList));
    runManyTimes(size, "guava  ", () -> guavaCheck(nullList));
    System.out.println("list is empty");
    runManyTimes(size, "if test", () -> runIfTest(emptyList));
    runManyTimes(size, "assert ", () -> assertEmptyList(emptyList));
    runManyTimes(size, "guava  ", () -> guavaCheck(emptyList));
    System.out.println("list is not empty");
    runManyTimes(size, "if test", () -> runIfTest(nonEmpty));
    runManyTimes(size, "assert ", () -> assertEmptyList(nonEmpty));
    runManyTimes(size, "guava  ", () -> guavaCheck(nonEmpty));
  }

  private void runManyTimes(int size, String message, Runnable runnable) {
    long start = System.nanoTime();
    for (int i = 0; i < size; i++) {
      runnable.run();
    }
    String timing = TimingUtil.millisSinceStart(start, 3);
    System.out.println("\ttime " + message + ": " + StringUtils.leftPad(timing, 8) + " ms");
  }

  private void guavaCheck(List list) {
    try {
      Preconditions.condition(list != null && !list.isEmpty(), "list cannot be empty or null");
    }
    catch (RuntimeException ignore) {
    }
  }

  private void runIfTest(List list) {
    try {
      if (list == null || list.isEmpty())
        throw new RuntimeException("list cannot be empty or null");
    }
    catch (RuntimeException ignore) {
    }
  }

  private void assertEmptyList(List list) {
//    try {
//      assertThat(list).isNotEmpty();
      assertThat(list).as("list").isNotEmpty();
//    }
//    catch (AssertionError ignore) {
//    }
  }

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