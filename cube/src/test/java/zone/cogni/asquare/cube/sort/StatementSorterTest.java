package zone.cogni.asquare.cube.sort;


import com.google.common.collect.ImmutableMap;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import zone.cogni.sem.jena.JenaUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatementSorterTest {

  @Test
  public void blank_root_blocks() {
    // given
    Model model = loadModel("digest/blank-root-blocks.ttl");

    // when
    List<Statement> statements = new StatementSorter().apply(model);
    print(statements);

    // then
    assertThat(statements.size()).isEqualTo(4);
    assertRowHasSubjectId(statements, 1, "3a7fda2ca088465c9d5f8f92fd7e0a929d874e79f7e358c953393169961858fc");
    assertRowHasSubjectId(statements, 2, "3a7fda2ca088465c9d5f8f92fd7e0a929d874e79f7e358c953393169961858fc");
    assertRowHasSubjectId(statements, 3, "cdc14bedd0be3c4227202577c59344500a2e110bc50b010d600d004ba4f67ebe");
  }

  @Test
  public void nested_blocks() {
    // given
    Model model = loadModel("digest/nested-blocks.ttl");

    // when
    List<Statement> statements = new StatementSorter().apply(model);
    print(statements);

    // then
    assertThat(statements.size()).isEqualTo(8);
    assertRowHasSubjectId(statements, 3, "6ce86f5d0f83dcd7dc47e05c2f34384450532646425bbf3313ed2fe49c5dd602");
    assertRowHasSubjectId(statements, 4, "9a6c2de7af7d96fa54d101f5280f1ca152546df474d215c661bcb8dd2b11da50");
    assertRowHasSubjectId(statements, 5, "9a6c2de7af7d96fa54d101f5280f1ca152546df474d215c661bcb8dd2b11da50");
    assertRowHasSubjectId(statements, 6, "a9cdeda412f7adcbab10e8fdc60d2117bb4d8b0764f2340c7ff2a9ac3924a775");
    assertRowHasSubjectId(statements, 7, "a9cdeda412f7adcbab10e8fdc60d2117bb4d8b0764f2340c7ff2a9ac3924a775");
  }

  @Test
  public void skos() {
    // given
    Model rdfXml = loadModel("digest/skos.rdf");

    // when
    List<Statement> statements = new StatementSorter().apply(rdfXml);
    print(statements);

    // then
    assertThat(statements.size()).isEqualTo(252);

    assertRowHasSubjectId(statements, 246, "6b7545da78e90a4b18b6aca7816f8d353b33eb7b0d5c8d90e6c9d835f1236e45");
    assertRowHasSubjectId(statements, 247, "6b7545da78e90a4b18b6aca7816f8d353b33eb7b0d5c8d90e6c9d835f1236e45");
    assertRowHasSubjectId(statements, 248, "7ddcc4c507b0986b8055c0c09c04cfbd7c75fe475a133e398810397f6ada922e");
    assertRowHasSubjectId(statements, 249, "7ddcc4c507b0986b8055c0c09c04cfbd7c75fe475a133e398810397f6ada922e");
    assertRowHasSubjectId(statements, 250, "8ecc67a2719e5c6479298355099d29b1a2efd70c9e84da5502b140369e03f137");
    assertRowHasSubjectId(statements, 251, "8ecc67a2719e5c6479298355099d29b1a2efd70c9e84da5502b140369e03f137");
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

    String rdf = new SortedStatementsString(namespaces).apply(statements);

    System.out.println("--------------------------------------------------------------------------------");
    System.out.println(rdf);
    System.out.println("--------------------------------------------------------------------------------");
  }

  private Model loadModel(String path) {
    return JenaUtils.read(new ClassPathResource(path));
  }

}