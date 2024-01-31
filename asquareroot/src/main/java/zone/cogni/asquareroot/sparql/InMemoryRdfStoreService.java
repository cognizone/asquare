package zone.cogni.asquareroot.sparql;

import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.Query;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.virtuoso.VirtuosoRdfStoreService;
import zone.cogni.libs.sparqlservice.impl.JenaModelSparqlService;
import zone.cogni.sem.jena.template.JenaResultSetHandler;

public class InMemoryRdfStoreService extends VirtuosoRdfStoreService implements RdfStoreService {
  private static final Logger log = LoggerFactory.getLogger(InMemoryRdfStoreService.class);
  private final JenaModelSparqlService jmss;

  public InMemoryRdfStoreService(JenaModelSparqlService jenaModelSparqlService) {
    super("", "", "");
    this.jmss = jenaModelSparqlService;
  }

  public InMemoryRdfStoreService(JenaModelSparqlService jenaModelSparqlService, String host, String user, String password) {
    super(host, user, password);
    this.jmss = jenaModelSparqlService;
  }

  @Override
  public void addData(Model model) {
    jmss.upload(model, "");
  }

  @Override
  public void addData(Model model, String graphUri) {
    jmss.upload(model, graphUri);
  }

  @Override
  public <R> R executeSelectQuery(Query query,
                                  QuerySolutionMap bindings,
                                  JenaResultSetHandler<R> resultSetHandler,
                                  String context) {
    log.debug("Select [{}] - {} \n{}", context, bindings, query);
    return jmss.executeSelectQuery(buildQuery(query, bindings).toString(), resultSet -> resultSetHandler.handle(resultSet));
  }

  @Override
  public boolean executeAskQuery(Query query, QuerySolutionMap bindings) {
    return jmss.executeAskQuery(buildQuery(query, bindings).toString());
  }

  @Override
  public Model executeConstructQuery(Query query, QuerySolutionMap bindings) {
    return jmss.executeConstructQuery(buildQuery(query, bindings).toString());
  }

  private Query buildQuery(Query query, QuerySolutionMap bindings) {
    ParameterizedSparqlString string = new ParameterizedSparqlString(query.toString(), bindings);
    return string.asQuery();
  }

  @Override
  public void executeUpdateQuery(String updateQuery) {
    jmss.executeUpdateQuery(updateQuery);
  }

  @Override
  public void delete() {
    throw new UnsupportedOperationException("unsupported operation");
  }

  @Override
  public void deleteGraph(String graphUri) {  //we can overwrite this in the implementation if we have a better way of checking this
    executeUpdateQuery(String.format("CLEAR GRAPH <%s>;", graphUri));
  }

  @Override
  public void replaceGraph(String graphUri, Model model) {  //we can overwrite this in the implementation if we have a better way of checking this
    deleteGraph(graphUri);
    addData(model, graphUri);
  }
}
