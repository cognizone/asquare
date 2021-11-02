package zone.cogni.asquare.cube.digest;


import com.google.common.collect.ImmutableMap;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import zone.cogni.asquare.cube.sort.StatementSorter;
import zone.cogni.asquare.cube.sort.SortedStatementsString;
import zone.cogni.sem.jena.JenaUtils;

import java.util.List;

class StatementSorterTest {

  @Test
  public void blank_root_blocks() {
    Model model = loadModel("digest/blank-root-blocks.ttl");

    List<Statement> statements = new StatementSorter().apply(model);
    print(statements);
  }

  @Test
  public void nested_blocks() {
    Model model = loadModel("digest/nested-blocks.ttl");

    List<Statement> statements = new StatementSorter().apply(model);
    print(statements);
  }

  @Test
  public void skos() {
    // given
    Model rdfXml = loadModel("digest/skos.rdf");

    // when
    List<Statement> statements = new StatementSorter().apply(rdfXml);
    print(statements);
  }

  private void print(List<Statement> statements) {
    ImmutableMap<String, String> namespaces = ImmutableMap.of("dct", "http://purl.org/dc/terms/",
                                                              "rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                                                              "owl", "http://www.w3.org/2002/07/owl#",
                                                              "skos", "http://www.w3.org/2004/02/skos/core#",
                                                              "rdfs", "http://www.w3.org/2000/01/rdf-schema#");

    System.out.println("--------------------------------------------------------------------------------");
    System.out.println(new SortedStatementsString(namespaces).apply(statements));
    System.out.println("--------------------------------------------------------------------------------");
  }

  private Model loadModel(String path) {
    return JenaUtils.read(new ClassPathResource(path));
  }

}