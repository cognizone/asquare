package zone.cogni.libs.sparqlservice.impl;

import java.util.function.Function;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDFS;
import org.assertj.core.util.Files;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import zone.cogni.libs.sparqlservice.SparqlService;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.apache.jena.rdf.model.ResourceFactory.createResource;
import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractSparqlServiceTest {

  protected abstract SparqlService getSUT();


  private static String r(final String localName) {
    return "https://example.org/" + localName;
  }

  @Test
  public void testAskQueryIsCorrectlyEvaluated() {
    final boolean result = getSUT().executeAskQuery(
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> ASK { GRAPH ?g { <https://example.org/c1> rdfs:subClassOf <https://example.org/c2> } }");
    assertTrue(result);
  }

  @Test
  public void testUpdateInsertsDataCorrectly() {
    assertFalse(getSUT().executeAskQuery(
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> ASK { GRAPH <https://example.org/m1> { <https://example.org/c1> rdfs:subClassOf <https://example.org/c3> } }"));
    getSUT().executeUpdateQuery(
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> INSERT DATA { GRAPH <https://example.org/m1> { <https://example.org/c1> rdfs:subClassOf <https://example.org/c3> } }");
    assertTrue(getSUT().executeAskQuery(
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> ASK { GRAPH <https://example.org/m1> { <https://example.org/c1> rdfs:subClassOf <https://example.org/c3> } }"));
    getSUT().executeUpdateQuery(
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> DELETE DATA { GRAPH <https://example.org/m1> { <https://example.org/c1> rdfs:subClassOf <https://example.org/c3> } }");
    assertFalse(getSUT().executeAskQuery(
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> ASK { GRAPH <https://example.org/m1> { <https://example.org/c1> rdfs:subClassOf <https://example.org/c3> } }"));
  }

  @Test
  public void testUploadTtlFileWorksCorrectly() throws IOException {
    final File dir = Files.newTemporaryFolder();
    final String fileName = "testUploadTtlFileWorksCorrectly.ttl";
    final File file = Files.newFile(dir.getPath() + File.separator + fileName);
    try {
      final Model model = ModelFactory.createDefaultModel();
      model.add(createResource(r("c1")), RDFS.comment, "comment");
      model.write(new FileWriter(file), "TURTLE");

      final String checkTripleExists = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> ASK { GRAPH <"+ file.toURI() + "> { <https://example.org/c1> rdfs:comment 'comment' } }";

      assertFalse(getSUT().executeAskQuery(checkTripleExists));
      getSUT().uploadTtlFile(file);
      assertTrue(getSUT().executeAskQuery(checkTripleExists));
    } finally {
      getSUT().dropGraph(file.toURI().toString());
      file.delete();
    }
  }

  @Test
  public void testUploadTtlFileThrowsRuntimeExceptionIfTheFileWasNotFound() {
    assertThrows(RuntimeException.class,
        () -> {
          final File file = File.createTempFile("fusekisparqlservicetest-", ".ttl");
          file.delete();
          getSUT().uploadTtlFile(file);
        });
  }

  @Test
  public void testReplaceGraphReplaceGraphCorrectly() {
    final Model model = ModelFactory.createDefaultModel();
    model.add(createResource(r("c1")), RDFS.label, "Class 1 - label 2");
    model.add(createResource(r("c1")), RDFS.label, "Class 1 - label 3");

    final String check =
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> ASK { GRAPH <" + r("m2")
            + "> { <https://example.org/c1> rdfs:label 'Class 1 - label 2' . <https://example.org/c1> rdfs:label 'Class 1 - label 3' FILTER NOT EXISTS { <https://example.org/c1> rdfs:label 'Class 1' } } }";

    assertFalse(getSUT().executeAskQuery(check));
    getSUT().replaceGraph(r("m2"), model);
    assertTrue(getSUT().executeAskQuery(check));
  }

  @Test
  public void testUpdateGraphUpdatesGraphCorrectly() {
    final Model model = ModelFactory.createDefaultModel();
    model.add(createResource(r("c1")), RDFS.label, "Class 1 - label 2");
    model.add(createResource(r("c1")), RDFS.label, "Class 1 - label 3");

    final String check =
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> ASK { GRAPH <" + r("m2")
            + "> { <https://example.org/c1> rdfs:label 'Class 1 - label 2' . <https://example.org/c1> rdfs:label 'Class 1 - label 3' . <https://example.org/c1> rdfs:label 'Class 1' } }";

    assertFalse(getSUT().executeAskQuery(check));
    getSUT().updateGraph(r("m2"), model);
    assertTrue(getSUT().executeAskQuery(check));
  }

  @Test
  public void testSelectQueryReturnsResultsFromRespectiveGraphs() {
    final ResultSet result = getSUT().executeSelectQuery(
        "SELECT * { GRAPH ?g { ?s ?p ?o } FILTER (?g in (<https://example.org/m1>, <https://example.org/m2>))}", Function.identity());

    while (result.hasNext()) {
      result.next();
    }
    Assertions.assertEquals(2, result.getRowNumber());
  }

  @Test
  public void testQueryForModelReturnsResultsFromRespectiveGraphs() {
    final Model model = getSUT().queryForModel("CONSTRUCT { ?s ?p ?o } WHERE { GRAPH ?g { ?s ?p ?o } FILTER (?g in (<https://example.org/m1>, <https://example.org/m2>)) }");
    Assertions.assertEquals(2, model.size());
  }
}