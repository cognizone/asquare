package zone.cogni.asquare.triplestore.sparqlendpoint;

import com.google.common.base.Preconditions;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.engine.http.QueryEngineHTTP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.sem.jena.template.JenaResultSetHandler;

public class SparqlEndpointRdfStoreService implements RdfStoreService {

  private static final Logger log = LoggerFactory.getLogger(SparqlEndpointRdfStoreService.class);

  private String rdfStoreUrl;
  private String modelContentType;

  public SparqlEndpointRdfStoreService() {
  }

  public SparqlEndpointRdfStoreService(String rdfStoreUrl) {
    this.rdfStoreUrl = rdfStoreUrl;
  }

  public void setRdfStoreUrl(String rdfStoreUrl) {
    this.rdfStoreUrl = rdfStoreUrl;
  }

  public SparqlEndpointRdfStoreService setModelContentType(String modelContentType) {
    this.modelContentType = modelContentType;
    return this;
  }

  @Override
  public void addData(Model model) {
    throw new UnsupportedOperationException("add data");
  }

  @Override
  public void addData(Model model, String graphUri) {
    throw new UnsupportedOperationException("add data");
  }

  @Override
  public <R> R executeSelectQuery(Query query, QuerySolutionMap bindings, JenaResultSetHandler<R> resultSetHandler, String context) {
    Preconditions.checkNotNull(rdfStoreUrl);

    if (log.isDebugEnabled()) log.debug("Select {} - {} \n{}",
                                        context == null ? "" : "--- " + context + " --- ",
                                        bindings,
                                        query);

    try (QueryExecution queryExecution = QueryExecutionFactory.sparqlService(rdfStoreUrl, query)) {
      queryExecution.setInitialBinding(bindings);

      ResultSet resultSet = queryExecution.execSelect();
      return resultSetHandler.handle(resultSet);
    }
  }

  @Override
  public boolean executeAskQuery(Query query, QuerySolutionMap bindings) {
    Preconditions.checkNotNull(rdfStoreUrl);

    try (QueryExecution queryExecution = QueryExecutionFactory.sparqlService(rdfStoreUrl, query)) {
      queryExecution.setInitialBinding(bindings);
      return queryExecution.execAsk();
    }
  }

  @Override
  public Model executeConstructQuery(Query query, QuerySolutionMap bindings) {
    Preconditions.checkNotNull(rdfStoreUrl);

    try (QueryEngineHTTP queryExecution = (QueryEngineHTTP) QueryExecutionFactory.sparqlService(rdfStoreUrl, query)) {
      if (modelContentType != null) queryExecution.setModelContentType(modelContentType);
      if (!bindings.asMap().isEmpty()) queryExecution.setInitialBinding(bindings);
      return queryExecution.execConstruct();
    }
    catch (Exception e) {
      log.error("Query failed: \n{}", query);
      throw e;
    }
  }

  @Override
  public void executeUpdateQuery(String updateQuery) {
    throw new UnsupportedOperationException("update not supported");
  }

  @Override
  public void delete() {
    throw new UnsupportedOperationException("delete not supported");
  }
}
