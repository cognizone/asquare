package zone.cogni.asquare.cube.sort;


import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import zone.cogni.sem.jena.JenaUtils;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


class SortedStatementsStringTest {

  @Test
  public void blank_root_blocks() {
    // given
    Model model = loadModel("sort/mix-test.ttl");

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
    Model model = loadModel("sort/mix-test-2.ttl");

    // when
    List<Statement> statements = new StatementSorter().apply(model);
    String rdf = new SortedStatementsString().apply(statements);

    // then
    Model newModel = getModelFromString(rdf);
    assertThat(newModel.size()).isEqualTo(8);
  }

  @Test
  public void blank_nodes_as_skolem_uris() {
    // given
    Model model = loadModel("sort/blank-node-with-nested-blank-node-with-two-triples.ttl");

    // when
    String rdfString = SortedStatementsString.newBuilder()
                                             .withSkolemBaseUri("http://cogni.zone")
                                             .build()
                                             .apply(model);

    // then
    assertThat(rdfString).contains("<http://cogni.zone/.well-known/genid/548056f0698a0e410152cdeec9d9a8c9242dfe2b13f813658e40bfec1979856f>");
    assertThat(rdfString).contains("<http://cogni.zone/.well-known/genid/a66dae80794f76cb551de9078eec8a5c5c12bcdc10023b9170016461e99e184a>");
  }

  @Test
  public void skos() {
    // given
    Model rdfXml = loadModel("sort/skos.rdf");

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
    Model rdfXml = loadModel("sort/skos.rdf");

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

  @Test
  void skos_rdf_and_ttl() throws IOException {
    // given
    Model rdfXml = loadModel("sort/skos.rdf");
    Model ttl = loadModel("sort/skos.ttl");

    // when
    String rdfString = SortedStatementsString.newBuilder()
                                             .withBase("http://www.w3.org/2004/02/skos/core#")
                                             .withIndent(8)
                                             .build()
                                             .apply(rdfXml);

    String ttlString = SortedStatementsString.newBuilder()
                                             .withBase("http://www.w3.org/2004/02/skos/core#")
                                             .withIndent(8)
                                             .build()
                                             .apply(ttl);

    // then
    assertThat(rdfString).isEqualTo(ttlString);
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
