package zone.cogni.libs.sparqlservice.impl;

import java.io.StringWriter;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.Objects;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.jena.query.Dataset;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import zone.cogni.libs.core.utils.ApacheHttpClientUtils;
import zone.cogni.libs.sparqlservice.SparqlService;

@Disabled("An integration test dependent on a running Virtuoso instance. To run it manually, set the Config below properly and run the tests.")
public class VirtuosoSparqlServiceTest extends AbstractSparqlServiceTest {

  private static SparqlService sut;

  @BeforeEach
  public void init() throws URISyntaxException {
    final Config config = new Config();
    config.setUrl("http://localhost:8890/sparql-auth");
    config.setUser("dba");
    config.setPassword("dba");
    config.setGraphCrudUseBasicAuth(false);

    final Dataset dataset = RDFDataMgr.loadDataset(
        Objects.requireNonNull(AbstractSparqlServiceTest.class.getResource("/dataset.trig")).toURI()
            .toString());

    final Iterator<String> names = dataset.listNames();
    while (names.hasNext()) {
      final String name = names.next();
      final StringWriter w = new StringWriter();
      RDFDataMgr.write(w, dataset.getNamedModel(name), Lang.TURTLE);

      final String url = VirtuosoHelper.getVirtuosoUpdateUrl(config.getUrl(), name);
      ApacheHttpClientUtils.executeAuthenticatedPostOrPut(url, config.getUser(), config.getPassword(),
          config.isGraphCrudUseBasicAuth(), new ByteArrayEntity(w.toString().getBytes()), true,
          "text/turtle;charset=utf-8");
    }

    sut = new VirtuosoSparqlService(config);
  }

  @Override
  protected SparqlService getSUT() {
    return sut;
  }
}