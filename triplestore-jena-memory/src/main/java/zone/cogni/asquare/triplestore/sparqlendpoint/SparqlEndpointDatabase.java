package zone.cogni.asquare.triplestore.sparqlendpoint;

import com.google.common.base.Preconditions;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionBuilder;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.sem.jena.template.JenaResultSetHandler;

public class SparqlEndpointDatabase implements RdfStoreService {

  private static final Logger log = LoggerFactory.getLogger(SparqlEndpointDatabase.class);

  private String resource;

  //TODO:  NOT YET SUPPORTED !!
  private String modelContentType;

  public SparqlEndpointDatabase() {
  }

  public String getResource() {
    return resource;
  }

  public void setResource(String resource) {
    this.resource = resource;
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
    Preconditions.checkNotNull(resource);
    if (log.isDebugEnabled()) log.debug("Select {} - {} \n{}", context == null ? "" : "--- " + context + " --- ", bindings, query);
    QueryExecutionBuilder builder = QueryExecution.service(resource).query(query);
    applyBindings(builder, bindings);
    try (QueryExecution queryExecution = builder.build()) {
      ResultSet resultSet = queryExecution.execSelect();
      return resultSetHandler.handle(resultSet);
    }
  }

  @Override
  public boolean executeAskQuery(Query query, QuerySolutionMap bindings) {
    Preconditions.checkNotNull(resource);
    QueryExecutionBuilder builder = QueryExecution.service(resource).query(query);
    applyBindings(builder, bindings);
    try (QueryExecution queryExecution = builder.build()) {
      return queryExecution.execAsk();
    }
  }

  @Override
  public Model executeConstructQuery(Query query, QuerySolutionMap bindings) {
    Preconditions.checkNotNull(resource);
    //  TODO is it used?
    //   if (modelContentType != null) queryExecution.setModelContentType(modelContentType);

    QueryExecutionBuilder builder = QueryExecution.service(resource).query(query);
    applyBindings(builder, bindings);
    try (QueryExecution queryExecution = builder.build()) {
      return queryExecution.execConstruct();
    }
    catch (Exception e) {
      log.error("Query failed: \n{}", query);
      throw e;
    }
  }

  private void applyBindings(QueryExecutionBuilder builder, QuerySolutionMap bindings) {
    if (bindings == null || bindings.asMap().isEmpty()) return;
    builder.substitution(bindings);
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
