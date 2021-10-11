package zone.cogni.asquare.cube.digest;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import zone.cogni.asquare.triplestore.jenamemory.InternalRdfStoreService;
import zone.cogni.sem.jena.JenaUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelDigestTest {

  @Test
  public void illegal_model() {
    // given
    Model model = loadModel("digest/illegal-model.ttl");

    // when
    assertThatThrownBy(() -> {
      new ModelDigest().apply(model);
    }) // then
       .isInstanceOf(RuntimeException.class)
       .hasMessageContaining("blank node")
       .hasMessageContaining("used more than once");
  }

  @Test
  public void correct_model() {
    // given
    Model model = loadModel("digest/correct-model.ttl");

    // when
    String result = new ModelDigest().apply(model);

    // then
    assertThat(result).isEqualTo("8818b315f55c25fe70e239dd43ad025ae89cad5dc13bc0def5dff3cebf27f536");
  }

  @Test
  public void root_block() {
    // given
    Model model = loadModel("digest/root-block.ttl");

    // when (note: internal method is tested here)
    List<Map<String, RDFNode>> rows = SortedBlock.getRootStatements(new InternalRdfStoreService(model));

    // then
    assertThat(rows).size().isEqualTo(3);
    assertThat(rows).extracting("s").size().isEqualTo(3);

    assertThat(rows).anySatisfy(row -> {
      assertThat(row.get("s").asResource().getURI()).isEqualTo("http://demo.com/data/1");
    });

    assertThat(rows).anySatisfy(row -> {
      assertThat(row.get("s").asResource().getURI()).isEqualTo("http://demo.com/data/3");
    });

    assertThat(rows).anySatisfy(row -> {
      assertThat(row.get("s").isAnon()).isTrue();
      assertThat(row.get("o").asResource().getURI()).isEqualTo("http://demo.com/model/blank-type");
    });

    assertThat(rows).extracting("o")
                    .doesNotContain(ResourceFactory.createResource("<http://demo.com/model/other-blank-type>"));
  }

  @Test
  public void single_triple_lang() {
    // given
    Model model = loadModel("digest/single-triple-lang.ttl");

    // when
    String result = new ModelDigest().apply(model);

    // then
    String triple = "<http://demo.com/data/1> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> \"lang\"@en";
    assertThat(result).isEqualTo(DigestUtils.sha256Hex(DigestUtils.sha256Hex(triple)));
  }

  @Test
  public void single_triple_literal() {
    // given
    Model model = loadModel("digest/single-triple-literal.ttl");

    // when
    String result = new ModelDigest().apply(model);

    // then
    String triple = "<http://demo.com/data/1> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> \"12\"^^<http://www.w3.org/2001/XMLSchema#decimal>";
    assertThat(result).isEqualTo(DigestUtils.sha256Hex(DigestUtils.sha256Hex(triple)));
  }

  @Test
  public void single_triple_url() {
    // given
    Model model = loadModel("digest/single-triple-url.ttl");

    // when
    String result = new ModelDigest().apply(model);

    // then
    String triple = "<http://demo.com/data/1> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://demo.com/model/type>";
    assertThat(result).isEqualTo(DigestUtils.sha256Hex(DigestUtils.sha256Hex(triple)));
  }

  private Model loadModel(String path) {
    return JenaUtils.read(new ClassPathResource(path));
  }

}