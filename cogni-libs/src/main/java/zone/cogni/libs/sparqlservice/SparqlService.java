package zone.cogni.libs.sparqlservice;

import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;

import java.io.File;
import java.util.function.Function;

public interface SparqlService {

  void uploadTtlFile(File file);

  Model queryForModel(String query);

  void executeUpdateQuery(String updateQuery);

  boolean executeAskQuery(String updateQuery);

  /**
   * Deprecated because naming is a bit vague.
   * Please use {@link #updateGraph(String, Model)}.
   */
  @Deprecated
  void upload(Model model, String graphUri);

  <R> R executeSelectQuery(String query, Function<ResultSet, R> resultHandler);

  void dropGraph(String graphUri);

  /**
   * Updates an existing graph by adding triples of passed in model.
   * <p>
   * Note: please use with care since in most case you probably want to use #replaceGraph
   * </p>
   * @param graphUri uri of graph being updated
   * @param model model which is being added to the current graph
   */
  default void updateGraph(String graphUri, Model model) {
    upload(model, graphUri);
  }

  /**
   * <p>
   * Replaces current model in a graph with the model passed in as a parameter.
   * </p>
   * <p>
   * Please note:
   * <ul>
   *   <li>default implementation is not considered to be robust and should be overridden</li>
   *   <li>
   *     new method was introduced because a lot of projects are actually trying to emulate a replace
   *     by doing a manual {@link #dropGraph(String)} and {@link #upload(Model, String)}.
   *   </li>
   * </ul>
   * </p>
   *
   * @param graphUri uri of graph being updated
   * @param model new model which will be in the designated graph.
   */
  default void replaceGraph(String graphUri, Model model) {
    dropGraph(graphUri);
    updateGraph(graphUri, model);
  }
}
