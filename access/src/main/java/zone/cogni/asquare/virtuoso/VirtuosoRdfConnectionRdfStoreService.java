package zone.cogni.asquare.virtuoso;

import java.net.URI;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.http.auth.AuthEnv;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionRemote;
import zone.cogni.libs.sparqlservice.impl.Config;
import zone.cogni.libs.sparqlservice.impl.VirtuosoHelper;
import zone.cogni.sem.jena.template.JenaResultSetHandler;

/**
 * Implementation of VirtuosoRdfStoreService backed by Jena RDFConnection.
 * <p>
 * To be tested: Should be transactional (autocommit). Might be less performant than
 * {@link zone.cogni.asquare.virtuoso.VirtuosoApacheHttpClientRdfStoreService}.
 */
public class VirtuosoRdfConnectionRdfStoreService implements VirtuosoRdfStoreService {

  private final Config config;

  public VirtuosoRdfConnectionRdfStoreService(String rdfStoreUrl, String rdfStoreUser,
      String rdfStorePassword, boolean graphCrudUseBasicAuth) {
    config = new Config();
    config.setUrl(rdfStoreUrl);
    config.setUser(rdfStoreUser);
    config.setPassword(rdfStorePassword);
    config.setGraphCrudUseBasicAuth(graphCrudUseBasicAuth);
    AuthEnv.get()
        .registerUsernamePassword(URI.create(StringUtils.substringBeforeLast(config.getUrl(), "/")),
            config.getUser(), config.getPassword());
  }

  protected RDFConnection getConnection() {
    return RDFConnectionRemote.newBuilder().queryEndpoint(config.getUrl())
        .updateEndpoint(config.getUrl()).destination(config.getUrl())
        .gspEndpoint(VirtuosoHelper.getVirtuosoGspFromSparql(config.getUrl())).build();
  }

  protected RDFConnection getConstructConnection() {
    return RDFConnectionRemote.newBuilder().queryEndpoint(config.getUrl())
        .updateEndpoint(config.getUrl()).destination(config.getUrl())
        .acceptHeaderQuery("text/turtle")
        .gspEndpoint(VirtuosoHelper.getVirtuosoGspFromSparql(config.getUrl())).build();
  }


  @Override
  public void addData(Model model) {
    addData(model, "");
  }

  @Override
  public void addData(Model model, String graphUri) {
    try (RDFConnection connection = getConnection();) {
      connection.load(graphUri, VirtuosoHelper.patchModel(model));
    }
  }

  protected Query buildQuery(Query query, QuerySolutionMap bindings) {
    return new ParameterizedSparqlString(query.toString(), bindings).asQuery();
  }

  @Override
  public <R> R executeSelectQuery(Query query, QuerySolutionMap bindings,
      JenaResultSetHandler<R> resultSetHandler, String context) {
    try (RDFConnection connection = getConnection(); QueryExecution queryExecution = connection.query(
        buildQuery(query, bindings))) {
      return resultSetHandler.handle(queryExecution.execSelect());
    }
  }

  @Override
  public boolean executeAskQuery(Query query, QuerySolutionMap bindings) {
    try (RDFConnection connection = getConnection(); QueryExecution queryExecution = connection.query(
        buildQuery(query, bindings))) {
      return queryExecution.execAsk();
    }
  }

  @Override
  public Model executeConstructQuery(Query query, QuerySolutionMap bindings) {
    try (RDFConnection connection = getConstructConnection(); QueryExecution queryExecution = connection.query(
        buildQuery(query, bindings))) {
      return queryExecution.execConstruct();
    }
  }

  @Override
  public void executeUpdateQuery(String updateQuery) {
    try (RDFConnection connection = getConnection()) {
      connection.update(updateQuery);
    }
  }

  @Override
  public void delete() {
    try (RDFConnection connection = getConnection()) {
      connection.delete();
    }
  }
}
