package zone.cogni.asquare.virtuoso;

import static org.apache.jena.rdf.model.ResourceFactory.createResource;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static zone.cogni.libs.sparqlservice.impl.VirtuosoHelper.getVirtuosoUpdateUrl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.Objects;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDFS;
import org.assertj.core.util.Files;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.libs.core.utils.ApacheHttpClientUtils;
import zone.cogni.libs.sparqlservice.impl.Config;

public abstract class VirtuosoRdfStoreServiceTest {

  private static VirtuosoRdfStoreService sut;

  protected abstract VirtuosoRdfStoreService getVirtuosoRdfStoreService(final String url,
      final String user, final String password, final boolean useBasicAuth);

  @BeforeEach
  public void init() throws URISyntaxException {
    final Config config = new Config();
    config.setUrl("http://localhost:8890/sparql-auth");
    config.setUser("dba");
    config.setPassword("dba");
    config.setGraphCrudUseBasicAuth(false);

    final Dataset dataset = RDFDataMgr.loadDataset(
        Objects.requireNonNull(getClass().getResource("/dataset.trig")).toURI()
            .toString());

    final Iterator<String> names = dataset.listNames();
    while (names.hasNext()) {
      final String name = names.next();
      final StringWriter w = new StringWriter();
      RDFDataMgr.write(w, dataset.getNamedModel(name), Lang.TURTLE);

      final String url = getVirtuosoUpdateUrl(config.getUrl(), name);
      ApacheHttpClientUtils.executeAuthenticatedPostOrPut(url, config.getUser(),
          config.getPassword(),
          config.isGraphCrudUseBasicAuth(), new ByteArrayEntity(w.toString().getBytes()), true,
          "text/turtle;charset=utf-8");
    }

    sut = getVirtuosoRdfStoreService(config.getUrl(), config.getUser(), config.getPassword(),
        config.isGraphCrudUseBasicAuth());
  }

  protected RdfStoreService getSUT() {
    return sut;
  }


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

      String graphIri = "urn:file:" + fileName;
      getSUT().deleteGraph(graphIri);

      final String checkTripleExists =
          "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> ASK { GRAPH <" + graphIri
              + "> { <https://example.org/c1> rdfs:comment 'comment' } }";

      assertFalse(getSUT().executeAskQuery(checkTripleExists));
      getSUT().addData(RDFDataMgr.loadModel(file.toURI().toString()), graphIri);
      assertTrue(getSUT().executeAskQuery(checkTripleExists));
    } finally {
      getSUT().deleteGraph(file.toURI().toString());
      file.delete();
    }
  }

  @Test
  public void testUploadTtlFileThrowsRuntimeExceptionIfTheFileWasNotFound() {
    assertThrows(RuntimeException.class,
        () -> {
          final File file = File.createTempFile("fusekisparqlservicetest-", ".ttl");
          file.delete();
          getSUT().addData(RDFDataMgr.loadModel(file.toURI().toString()));
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
    getSUT().addData(model, r("m2"));
    assertTrue(getSUT().executeAskQuery(check));
  }

  @Test
  public void testSelectQueryReturnsResultsFromRespectiveGraphs() {
    final ResultSet result = getSUT().executeSelectQuery(
        "SELECT * { GRAPH ?g { ?s ?p ?o } FILTER (?g in (<https://example.org/m1>, <https://example.org/m2>))}",
        resultSet -> resultSet);

    while (result.hasNext()) {
      result.next();
    }
    Assertions.assertEquals(2, result.getRowNumber());
  }

  @Test
  public void testQueryForModelReturnsResultsFromRespectiveGraphs() {
    final Model model = getSUT().executeConstructQuery(
        "CONSTRUCT { ?s ?p ?o } WHERE { GRAPH ?g { ?s ?p ?o } FILTER (?g in (<https://example.org/m1>, <https://example.org/m2>)) }");
    Assertions.assertEquals(2, model.size());
  }
}
