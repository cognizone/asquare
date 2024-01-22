package zone.cogni.libs.sparqlservice.impl;

import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zone.cogni.libs.sparqlservice.SparqlService;

import java.net.URISyntaxException;
import java.util.function.Function;

public class FusekiSparqlServiceTest extends AbstractSparqlServiceTest {

  private static FusekiServer server;

  private static SparqlService sut;

  @BeforeEach
  public void init() throws URISyntaxException {
    server = FusekiServer.create().port(12345).add("/rdf", AbstractSparqlServiceTest.createDataset()).build();
    server.start();
    final FusekiConfig config = new FusekiConfig();
    config.setUrl("http://localhost:12345/rdf");
    sut = new FusekiSparqlService(config);
  }

  @Override
  protected SparqlService getSUT() {
    return sut;
  }

  @AfterEach
  public void destroy() {
    server.stop();
  }

  @Test
  public void testSelectQueryReturnsResultsFromDefaultGraphOnly() {
    final ResultSet result = getSUT().executeSelectQuery("SELECT * { ?s ?p ?o }", Function.identity());
    while (result.hasNext()) {
      result.next();
    }
    Assertions.assertEquals(1, result.getRowNumber());
  }

  @Test
  public void testQueryForModelReturnsDefaultGraphOnly() {
    final Model model = getSUT().queryForModel("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }");
    Assertions.assertEquals(1, model.size());
  }
}