package zone.cogni.libs.sparqlservice.impl;

import java.io.File;
import java.util.function.Function;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdfconnection.RDFConnection;
import zone.cogni.libs.sparqlservice.SparqlService;

/**
 * Implementation of SparqlService based on Jena RDFConnection API.
 */
public abstract class RDFConnectionSparqlService implements SparqlService {

  /**
   * Provides an RDFConnection instance for the given endpoint.
   *
   * @return RDFConnection instance.
   */
  protected abstract RDFConnection getConnection();

  /**
   * Provides an RDFConnection instance for CONSTRUCT queries for the given endpoint.
   * This was introduced due to incorrect accept headers when testing against Virtuoso.
   *
   * Ideally should be removed.
   *
   * @return RDFConnection instance.
   */
  protected abstract RDFConnection getConstructConnection();

  @Override
  public void uploadTtlFile(File file) {
    try (RDFConnection connection = getConnection();) {
      connection.load(file.toURI().toString(), file.getPath());
    }
  }

  @Override
  public Model queryForModel(String query) {
    try (RDFConnection connection = getConstructConnection();
        QueryExecution queryExecution = connection.query(query)) {
      return queryExecution.execConstruct();
    }
  }

  @Override
  public void executeUpdateQuery(String query) {
    try (RDFConnection connection = getConnection()) {
      connection.update(query);
    }
  }

  @Override
  public void upload(Model model, String graphUri) {
    try (RDFConnection connection = getConnection()) {
      connection.load(graphUri, model);
    }
  }

  @Override
  public <R> R executeSelectQuery(String query, Function<ResultSet, R> resultHandler) {
    try (RDFConnection connection = getConnection();
        QueryExecution queryExecution = connection.query(
        query)) {
      return resultHandler.apply(queryExecution.execSelect());
    }
  }

  @Override
  public boolean executeAskQuery(String askQuery) {
    try (RDFConnection connection = getConnection();
        QueryExecution queryExecution = connection.query(
        askQuery)) {
      return queryExecution.execAsk();
    }
  }

  @Override
  public void dropGraph(String graphUri) {
    try (RDFConnection connection = getConnection()) {
      connection.delete(graphUri);
    }
  }

  @Override
  public void replaceGraph(String graphUri, Model model) {
    try (RDFConnection connection = getConnection()) {
      connection.put(graphUri, model);
    }
  }
}
