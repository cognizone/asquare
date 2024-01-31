package zone.cogni.libs.sparqlservice.impl;

import java.net.URI;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.http.auth.AuthEnv;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionRemote;
import zone.cogni.libs.sparqlservice.SparqlService;

public class VirtuosoSparqlService extends RDFConnectionSparqlService implements
    SparqlService {

  private final Config config;

  public VirtuosoSparqlService(Config config) {
    this.config = config;
    AuthEnv.get()
        .registerUsernamePassword(URI.create(StringUtils.substringBeforeLast(config.getUrl(), "/")),
            this.config.getUser(), this.config.getPassword());
  }

  protected RDFConnection getConnection() {
    return RDFConnectionRemote
        .newBuilder()
        .queryEndpoint(config.getUrl())
        .updateEndpoint(config.getUrl())
        .destination(config.getUrl())
        .gspEndpoint(VirtuosoHelper.getVirtuosoGspFromSparql(config.getUrl()))
        .build();
  }

  protected RDFConnection getConstructConnection() {
    return RDFConnectionRemote
        .newBuilder()
        .queryEndpoint(config.getUrl())
        .updateEndpoint(config.getUrl())
        .destination(config.getUrl())
        .acceptHeaderQuery("text/turtle")
        .gspEndpoint(VirtuosoHelper.getVirtuosoGspFromSparql(config.getUrl()))
        .build();
  }
}
