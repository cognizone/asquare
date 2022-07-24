package zone.cogni.asquare.cube.sort;


import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import zone.cogni.sem.jena.JenaUtils;

import java.io.StringReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


class SortedStatementsStringTest {

  @Test
  public void blank_root_blocks() {
    // given
    Model model = loadModel("digest/blank-root-blocks.ttl");

    // when
    List<Statement> statements = new StatementSorter().apply(model);
    String rdf = new SortedStatementsString().apply(statements);

    // then
    Model newModel = getModelFromString(rdf);
    assertThat(newModel.size()).isEqualTo(4);
  }

  @Test
  public void nested_blocks() {
    // given
    Model model = loadModel("digest/nested-blocks.ttl");

    // when
    List<Statement> statements = new StatementSorter().apply(model);
    String rdf = new SortedStatementsString().apply(statements);

    // then
    Model newModel = getModelFromString(rdf);
    assertThat(newModel.size()).isEqualTo(8);
  }

  @Test
  public void skos() {
    // given
    Model rdfXml = loadModel("digest/skos.rdf");

    // when
    List<Statement> statements = new StatementSorter().apply(rdfXml);
    String rdf = new SortedStatementsString().apply(statements);

    // then
    Model newModel = getModelFromString(rdf);
    assertThat(newModel.size()).isEqualTo(252);
  }

  @Test
  public void skos_with_base() {
    // given
    Model rdfXml = loadModel("digest/skos.rdf");

    // when
    String rdf = SortedStatementsString.newBuilder()
                                       .withBase("http://www.w3.org/2004/02/skos/core#")
                                       .withIndent(8)
                                       .build()
                                       .apply(rdfXml);

    // then
    Model newModel = getModelFromString(rdf);
    assertThat(newModel.size()).isEqualTo(252);
  }

  private Model getModelFromString(String rdf) {
    Model model = ModelFactory.createDefaultModel();
    model.read(new StringReader(rdf), null, "TTL");
    return model;
  }

  private Model loadModel(String path) {
    return JenaUtils.read(new ClassPathResource(path));
  }

}