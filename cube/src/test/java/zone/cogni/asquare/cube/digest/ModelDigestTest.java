package zone.cogni.asquare.cube.digest;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import zone.cogni.sem.jena.JenaUtils;

import java.util.List;

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
    SortedBlock rootBlock = new SortedBlock(model);

    // then
    assertThat(rootBlock.getNestedBlocks()).size().isEqualTo(3);

    assertThat(rootBlock.getNestedBlocks()).anySatisfy(block -> {
      assertThat(block.getStatement().getSubject().getURI()).isEqualTo("http://demo.com/data/1");
    });

    assertThat(rootBlock.getNestedBlocks()).anySatisfy(block -> {
      assertThat(block.getStatement().getSubject().getURI()).isEqualTo("http://demo.com/data/3");
    });

    assertThat(rootBlock.getNestedBlocks()).anySatisfy(block -> {
      assertThat(block.getStatement()).isNull();
      assertThat(block.getNestedBlocks().size()).isEqualTo(1);

      SortedBlock blankNodeBlock = block.getNestedBlocks().get(0);
      assertThat(blankNodeBlock.getStatement().getSubject().isAnon()).isTrue();
      assertThat(blankNodeBlock.getStatement().getObject().asResource()
                               .getURI()).isEqualTo("http://demo.com/model/blank-type");
    });
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

  @Test
  public void nested_blocks() {
    // given
    Model model = loadModel("digest/nested-blocks.ttl");

    // when
    SortedBlock rootBlock = new SortedBlock(model);

    // then
    assertThat(rootBlock.getNestedBlocks().size()).isEqualTo(3);

    // triple with uri as object
    assertThat(rootBlock.getNestedBlocks()).anySatisfy(block -> {
      assertThat(block.getStatement().getSubject().getURI()).isEqualTo("http://demo.com/data/1");
      assertThat(block.getStatement().getObject().asResource().getURI()).isEqualTo("http://demo.com/data/5");
      assertThat(block.getNestedBlocks()).isEmpty();
    });

    // triple with blank node as object and no nesting
    assertThat(rootBlock.getNestedBlocks()).anySatisfy(block -> {
      assertThat(block.getStatement().getSubject().getURI()).isEqualTo("http://demo.com/data/1");
      assertThat(block.getStatement().getObject().isAnon()).isTrue();

      assertThat(block.getNestedBlocks().size()).isEqualTo(1);

      SortedBlock blankNodeBlock = block.getNestedBlocks().get(0);
      assertThat(blankNodeBlock.getStatement().getObject().asResource().getURI())
              .isEqualTo("http://demo.com/model/blank-type");
    });

    // triple with blank node as object and nesting!
    assertThat(rootBlock.getNestedBlocks()).anySatisfy(block -> {
      assertThat(block.getStatement().getSubject().getURI()).isEqualTo("http://demo.com/data/2");
      assertThat(block.getStatement().getObject().isAnon()).isTrue();

      List<SortedBlock> nestedBlocks = block.getNestedBlocks();
      assertThat(nestedBlocks.size()).isEqualTo(2);

      assertThat(nestedBlocks).anySatisfy(otherBlankTypeBlock -> {
        assertThat(otherBlankTypeBlock.getStatement().getObject().asResource().getURI())
                .isEqualTo("http://demo.com/model/other-blank-type");
      });

      assertThat(nestedBlocks).anySatisfy(blankNodeBlock -> {
        assertThat(blankNodeBlock.getStatement().getObject().isAnon()).isTrue();
        assertThat(blankNodeBlock.getNestedBlocks().size()).isEqualTo(2);
      });
    });
  }

  @Test
  public void blank_nested_blocks() {
    // given
    Model model = loadModel("digest/blank-nested-blocks.ttl");

    // when
    SortedBlock rootBlock = new SortedBlock(model);

    // then
    assertThat(rootBlock.getNestedBlocks().size()).isEqualTo(3);
  }

  @Test
  public void skos_in_two_flavours() {
    // given
    Model rdfXml = loadModel("digest/skos.rdf");
    Model turtle = loadModel("digest/skos.ttl");

    // when
    String rdfXmlDigest = new SortedBlock(rdfXml).getDigest();
    String turtleDigest = new SortedBlock(turtle).getDigest();

    // then
    assertThat(rdfXmlDigest).as("rdfXmlDigest")
                            .isEqualTo(turtleDigest).as("turtleDigest");
  }

  private Model loadModel(String path) {
    return JenaUtils.read(new ClassPathResource(path));
  }

}