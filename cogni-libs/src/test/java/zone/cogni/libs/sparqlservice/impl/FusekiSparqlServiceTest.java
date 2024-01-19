package zone.cogni.libs.sparqlservice.impl;

import static org.apache.jena.rdf.model.ResourceFactory.createResource;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.function.Function;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FusekiSparqlServiceTest {

  private static FusekiServer server;

  private static FusekiSparqlService sut;

  @BeforeEach
  public void init() {
    server = FusekiServer.create().port(12345).add("/rdf", createDataset()).build();
    server.start();
    final FusekiConfig config = new FusekiConfig();
    config.setUrl("http://localhost:12345/rdf");
    sut = new FusekiSparqlService(config);
  }

  private static String r(final String localName) {
    return "https://example.org/" + localName;
  }

  private static Dataset createDataset() {
    final Dataset ds = DatasetFactory.create();
    ds.getDefaultModel().add(createResource(r("c1")), RDFS.subClassOf, createResource(r("c2")));
    ds.addNamedModel(createResource(r("m")),
        ModelFactory.createDefaultModel().add(createResource(r("c1")), RDFS.label, "Class 1"));
    return ds;
  }

  @Test
  public void testAskQueryIsCorrectlyEvaluated() {
    final boolean result = sut.executeAskQuery(
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> ASK { <https://example.org/c1> rdfs:subClassOf <https://example.org/c2> }");
    assertTrue(result);
  }

  @Test
  public void testSelectQueryReturnsCorrectNumberOfResults() {
    final ResultSet result = sut.executeSelectQuery("SELECT * { ?s ?p ?o }", Function.identity());
    while (result.hasNext()) {
      result.next();
    }
    Assertions.assertEquals(1, result.getRowNumber());
  }

  @Test
  public void testQueryForModelReturnsDefaultGraph() {
    final Model model = sut.queryForModel("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }");
    Assertions.assertEquals(1, model.size());
  }

  @Test
  public void testUpdateInsertsDataCorrectly() {
    assertFalse(sut.executeAskQuery(
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> ASK { <https://example.org/c1> rdfs:subClassOf <https://example.org/c3> }"));
    sut.executeUpdateQuery(
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> INSERT DATA { <https://example.org/c1> rdfs:subClassOf <https://example.org/c3> }");
    assertTrue(sut.executeAskQuery(
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> ASK { <https://example.org/c1> rdfs:subClassOf <https://example.org/c3> }"));
    sut.executeUpdateQuery(
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> DELETE DATA { <https://example.org/c1> rdfs:subClassOf <https://example.org/c3> }");
    assertFalse(sut.executeAskQuery(
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> ASK { <https://example.org/c1> rdfs:subClassOf <https://example.org/c3> }"));
  }

  @Test
  public void testUploadTtlFileWorksCorrectly() throws IOException {
    final File file = File.createTempFile("fusekisparqlservicetest-", ".ttl");
    try {
      final Model model = ModelFactory.createDefaultModel();
      model.add(createResource(r("c1")), RDFS.comment, "comment");
      model.write(new FileWriter(file), "TURTLE");

      final String checkTripleExists = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> ASK { GRAPH ?g { <https://example.org/c1> rdfs:comment 'comment' } }";

      assertFalse(sut.executeAskQuery(checkTripleExists));
      sut.uploadTtlFile(file);
      assertTrue(sut.executeAskQuery(checkTripleExists));
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
          sut.uploadTtlFile(file);
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

    assertFalse(sut.executeAskQuery(check));
    sut.replaceGraph(r("m"), model);
    assertTrue(sut.executeAskQuery(check));
  }

  @Test
  public void testUpdateGraphUpdatesGraphCorrectly() {
    final Model model = ModelFactory.createDefaultModel();
    model.add(createResource(r("c1")), RDFS.label, "Class 1 - label 2");
    model.add(createResource(r("c1")), RDFS.label, "Class 1 - label 3");

    final String check =
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> ASK { GRAPH <" + r("m")
            + "> { <https://example.org/c1> rdfs:label 'Class 1 - label 2' . <https://example.org/c1> rdfs:label 'Class 1 - label 3' . <https://example.org/c1> rdfs:label 'Class 1' } }";

    assertFalse(sut.executeAskQuery(check));
    sut.updateGraph(r("m"), model);
    assertTrue(sut.executeAskQuery(check));
  }

  @AfterEach
  public void destroy() {
    server.stop();
  }
}