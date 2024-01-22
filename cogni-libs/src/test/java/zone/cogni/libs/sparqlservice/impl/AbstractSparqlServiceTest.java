package zone.cogni.libs.sparqlservice.impl;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.Test;
import zone.cogni.libs.sparqlservice.SparqlService;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;

import static org.apache.jena.rdf.model.ResourceFactory.createResource;
import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractSparqlServiceTest {

  protected abstract SparqlService getSUT();


  private static String r(final String localName) {
    return "https://example.org/" + localName;
  }

  protected static Dataset createDataset() throws URISyntaxException {
    return RDFDataMgr.loadDataset(Objects.requireNonNull(AbstractSparqlServiceTest.class.getResource("/dataset.trig")).toURI().toString());
  }

  @Test
  public void testAskQueryIsCorrectlyEvaluated() {
    final boolean result = getSUT().executeAskQuery(
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> ASK { <https://example.org/c1> rdfs:subClassOf <https://example.org/c2> }");
    assertTrue(result);
  }

  @Test
  public void testUpdateInsertsDataCorrectly() {
    assertFalse(getSUT().executeAskQuery(
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> ASK { <https://example.org/c1> rdfs:subClassOf <https://example.org/c3> }"));
    getSUT().executeUpdateQuery(
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> INSERT DATA { <https://example.org/c1> rdfs:subClassOf <https://example.org/c3> }");
    assertTrue(getSUT().executeAskQuery(
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> ASK { <https://example.org/c1> rdfs:subClassOf <https://example.org/c3> }"));
    getSUT().executeUpdateQuery(
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> DELETE DATA { <https://example.org/c1> rdfs:subClassOf <https://example.org/c3> }");
    assertFalse(getSUT().executeAskQuery(
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> ASK { <https://example.org/c1> rdfs:subClassOf <https://example.org/c3> }"));
  }

  @Test
  public void testUploadTtlFileWorksCorrectly() throws IOException {
    final File file = File.createTempFile("file:fusekisparqlservicetest-", ".ttl");
    try {
      final Model model = ModelFactory.createDefaultModel();
      model.add(createResource(r("c1")), RDFS.comment, "comment");
      model.write(new FileWriter(file), "TURTLE");

      final String checkTripleExists = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> ASK { GRAPH ?g { <https://example.org/c1> rdfs:comment 'comment' } }";

      assertFalse(getSUT().executeAskQuery(checkTripleExists));
      getSUT().uploadTtlFile(file);
      assertTrue(getSUT().executeAskQuery(checkTripleExists));
    } finally {
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
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> ASK { GRAPH <" + r("m")
            + "> { <https://example.org/c1> rdfs:label 'Class 1 - label 2' . <https://example.org/c1> rdfs:label 'Class 1 - label 3' FILTER NOT EXISTS { <https://example.org/c1> rdfs:label 'Class 1' } } }";

    assertFalse(getSUT().executeAskQuery(check));
    getSUT().replaceGraph(r("m"), model);
    assertTrue(getSUT().executeAskQuery(check));
  }

  @Test
  public void testUpdateGraphUpdatesGraphCorrectly() {
    final Model model = ModelFactory.createDefaultModel();
    model.add(createResource(r("c1")), RDFS.label, "Class 1 - label 2");
    model.add(createResource(r("c1")), RDFS.label, "Class 1 - label 3");

    final String check =
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> ASK { GRAPH <" + r("m")
            + "> { <https://example.org/c1> rdfs:label 'Class 1 - label 2' . <https://example.org/c1> rdfs:label 'Class 1 - label 3' . <https://example.org/c1> rdfs:label 'Class 1' } }";

    assertFalse(getSUT().executeAskQuery(check));
    getSUT().updateGraph(r("m"), model);
    assertTrue(getSUT().executeAskQuery(check));
  }
}