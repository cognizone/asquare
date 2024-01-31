package zone.cogni.asquare.virtuoso;

import org.apache.jena.query.Query;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.rdf.model.Model;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.libs.sparqlservice.impl.Config;
import zone.cogni.libs.sparqlservice.impl.VirtuosoSparqlService;
import zone.cogni.sem.jena.template.JenaResultSetHandler;

public class VirtuosoRdfStoreService implements RdfStoreService {

  private final SparqlRdfStoreService sparqlService;

  public VirtuosoRdfStoreService(String rdfStoreUrl, String rdfStoreUser, String rdfStorePassword) {
    this(rdfStoreUrl, rdfStoreUser, rdfStorePassword, false);
  }

  public VirtuosoRdfStoreService(String rdfStoreUrl, String rdfStoreUser, String rdfStorePassword, boolean graphCrudUseBasicAuth) {
    final Config config = new Config();
    config.setUrl(rdfStoreUrl);
    config.setUser(rdfStoreUser);
    config.setPassword(rdfStorePassword);
    config.setGraphCrudUseBasicAuth(graphCrudUseBasicAuth);
    sparqlService = new SparqlRdfStoreService(new VirtuosoSparqlService(config));
  }

  @Override
  public void addData(Model model) {
    sparqlService.addData(model);
  }

  @Override
  public void addData(Model model, String graphUri) {
    sparqlService.addData(model, graphUri);
  }

  @Override
  public <R> R executeSelectQuery(Query query, QuerySolutionMap bindings, JenaResultSetHandler<R> resultSetHandler,
                                  String context) {
    return sparqlService.executeSelectQuery(query, bindings, resultSetHandler, context);
  }

  @Override
  public boolean executeAskQuery(Query query, QuerySolutionMap bindings) {
    return sparqlService.executeAskQuery(query, bindings);
  }

  @Override
  public Model executeConstructQuery(Query query, QuerySolutionMap bindings) {
    return sparqlService.executeConstructQuery(query, bindings);
  }

  @Override
  public void executeUpdateQuery(String updateQuery) {
    sparqlService.executeUpdateQuery(updateQuery);
  }

  @Override
  public void delete() {
    sparqlService.delete();
  }
}
