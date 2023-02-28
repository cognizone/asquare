package zone.cogni.asquare.cube.sort;


import com.google.common.collect.ImmutableMap;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import zone.cogni.asquare.cube.digest.SortedBlock;
import zone.cogni.libs.jena.utils.JenaUtils;

import java.util.List;
import java.util.stream.Collectors;

import static org.apache.jena.rdf.model.ResourceFactory.createStringLiteral;
import static org.assertj.core.api.Assertions.assertThat;

class StatementSorterTest {


  @Test
  public void blank_node_one_triple() {
    // given
    Model model = loadModel("sort/blank-node-one-triple.ttl");

    // when
    List<Statement> statements = getSortedStatements(model);

    // then
    assertThat(statements.size()).isEqualTo(1);
    assertRowHasSubjectId(statements, 0, "f0e61495a5dedf729117501bcfdf757f2c6101d23d62863905f4c0acc1ac16ea");
  }


  @Test
  public void same_blanks_node_one_triple() {
    // given
    Model model = loadModel("sort/same-blank-nodes-one-triple.ttl");

    // when
    List<Statement> statements = getSortedStatements(model);

    // then
    assertThat(statements.size()).isEqualTo(1);
    assertRowHasSubjectId(statements, 0, "f0e61495a5dedf729117501bcfdf757f2c6101d23d62863905f4c0acc1ac16ea");
  }

  @Test
  public void blank_node_two_triples() {
    // given
    Model model = loadModel("sort/blank-node-two-triples.ttl");

    // when
    List<Statement> statements = getSortedStatements(model);

    // then
    assertThat(statements.size()).isEqualTo(2);
    assertRowHasSubjectId(statements, 0, "a66dae80794f76cb551de9078eec8a5c5c12bcdc10023b9170016461e99e184a");
    assertRowHasSubjectId(statements, 1, "a66dae80794f76cb551de9078eec8a5c5c12bcdc10023b9170016461e99e184a");
  }

  @Test
  public void same_blank_nodes_two_triples() {
    // given
    Model model = loadModel("sort/same-blank-nodes-two-triples.ttl");

    // when
    List<Statement> statements = getSortedStatements(model);

    // then
    assertThat(statements.size()).isEqualTo(2);
    assertRowHasSubjectId(statements, 0, "a66dae80794f76cb551de9078eec8a5c5c12bcdc10023b9170016461e99e184a");
    assertRowHasSubjectId(statements, 1, "a66dae80794f76cb551de9078eec8a5c5c12bcdc10023b9170016461e99e184a");
  }

  @Test
  public void blank_node_with_nested_blank_node_with_two_triples() {
    // given
    Model model = loadModel("sort/blank-node-with-nested-blank-node-with-two-triples.ttl");

    // when
    List<Statement> statements = getSortedStatements(model);

    // then
    assertThat(statements.size()).isEqualTo(3);
    assertRowHasSubjectId(statements, 0, "548056f0698a0e410152cdeec9d9a8c9242dfe2b13f813658e40bfec1979856f");
    assertRowHasSubjectId(statements, 1, "a66dae80794f76cb551de9078eec8a5c5c12bcdc10023b9170016461e99e184a");
    assertRowHasSubjectId(statements, 2, "a66dae80794f76cb551de9078eec8a5c5c12bcdc10023b9170016461e99e184a");
  }

  @Test
  public void same_blank_node_with_nested_blank_node_with_two_triples() {
    // given
    Model model = loadModel("sort/same-blank-node-with-nested-blank-node-with-two-triples.ttl");

    // when
    List<Statement> statements = getSortedStatements(model);

    // then
    assertThat(statements.size()).isEqualTo(3);
    assertRowHasSubjectId(statements, 0, "548056f0698a0e410152cdeec9d9a8c9242dfe2b13f813658e40bfec1979856f");
    assertRowHasSubjectId(statements, 1, "a66dae80794f76cb551de9078eec8a5c5c12bcdc10023b9170016461e99e184a");
    assertRowHasSubjectId(statements, 2, "a66dae80794f76cb551de9078eec8a5c5c12bcdc10023b9170016461e99e184a");
  }

  @Test
  public void same_list_test() {
    // given
    Model model = loadModel("sort/same-lists.ttl");
    model.write(System.out, "N-TRIPLE");

    // when
    List<Statement> statements = getSortedStatements(model);

    // then
    assertThat(statements.size()).isEqualTo(10);

    List<Statement> fr = getFilteredStatements(statements, null, RDF.first, createStringLiteral("fr"));
    assertThat(fr).size().isEqualTo(1);

    List<Statement> de = getFilteredStatements(statements, null, RDF.first, createStringLiteral("de"));
    assertThat(fr).size().isEqualTo(1);
  }

  @Test
  public void mix_test() {
    // given
    Model model = loadModel("sort/mix-test.ttl");

    // when
    List<Statement> statements = getSortedStatements(model);

    // then
    assertThat(statements.size()).isEqualTo(4);
    assertRowHasSubjectId(statements, 1, "3a7fda2ca088465c9d5f8f92fd7e0a929d874e79f7e358c953393169961858fc");
    assertRowHasSubjectId(statements, 2, "3a7fda2ca088465c9d5f8f92fd7e0a929d874e79f7e358c953393169961858fc");
    assertRowHasSubjectId(statements, 3, "cdc14bedd0be3c4227202577c59344500a2e110bc50b010d600d004ba4f67ebe");
  }

  private List<Statement> getSortedStatements(Model model) {
    // for printing the sorted blocks
    SortedBlock sortedBlock = SortedBlock.create(model);
    System.out.println(sortedBlock);

    // sort the statements
    List<Statement> statements = new StatementSorter().apply(model);
    print(statements);

    return statements;
  }

  private List<Statement> getFilteredStatements(List<Statement> statements,
                                                Resource subject, Property predicate, RDFNode object) {
    return statements.stream()
                     .filter(statement -> {
                       if (subject != null && !statement.getSubject().equals(subject)) return false;
                       if (predicate != null && !statement.getPredicate().equals(predicate)) return false;
                       if (object != null && !statement.getObject().equals(object)) return false;
                       return true;
                     }).collect(Collectors.toList());
  }

  private void assertRowHasSubjectId(List<Statement> statements, int row, String id) {
    assertThat(statements.get(row).getSubject().isAnon()).isTrue();
    assertThat(statements.get(row).getSubject().getId().getLabelString()).isEqualTo(id);
  }

  private void print(List<Statement> statements) {
    ImmutableMap<String, String> namespaces = ImmutableMap.<String, String>builder()
                                                          .put("dct", "http://purl.org/dc/terms/")
                                                          .put("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
                                                          .put("owl", "http://www.w3.org/2002/07/owl#")
                                                          .put("skos", "http://www.w3.org/2004/02/skos/core#")
                                                          .put("rdfs", "http://www.w3.org/2000/01/rdf-schema#")
                                                          .put("xsd", "http://www.w3.org/2001/XMLSchema#")
                                                          .build();

    String rdf = SortedStatementsString.newBuilder()
                                       .withNamespaces(namespaces)
                                       .build()
                                       .apply(statements);

    System.out.println("--------------------------------------------------------------------------------");
    System.out.println(rdf);
    System.out.println("--------------------------------------------------------------------------------");
  }

  private Model loadModel(String path) {
    return JenaUtils.read(new ClassPathResource(path));
  }

}
