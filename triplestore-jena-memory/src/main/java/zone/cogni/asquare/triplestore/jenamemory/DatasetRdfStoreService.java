package zone.cogni.asquare.triplestore.jenamemory;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.shared.Lock;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.libs.jena.utils.DatasetHelper;
import zone.cogni.sem.jena.template.JenaResultSetHandler;

import java.util.function.Supplier;

public class DatasetRdfStoreService implements RdfStoreService {
  private final Dataset dataset;

  public DatasetRdfStoreService(Dataset dataset) {
    this.dataset = dataset;
  }

  @Override
  public void addData(Model model) {
    dataset.setDefaultModel(model);
  }

  public void addData(String graphUri, Model model) {
    dataset.addNamedModel(graphUri, model);
  }

  @Override
  public void addData(Model model, String graphUri) {
    dataset.addNamedModel(graphUri, model);
  }

  @Override
  public <R> R executeSelectQuery(Query query,
                                  QuerySolutionMap bindings,
                                  JenaResultSetHandler<R> resultSetHandler,
                                  String context) {
    return executeInLock(Lock.READ, () -> {
      traceQuery("Select", query, bindings, context);

      try (QueryExecution queryExecution = QueryExecutionFactory.create(query, dataset, bindings)) {
        return resultSetHandler.handle(queryExecution.execSelect());
      }
    });
  }

  @Override
  public boolean executeAskQuery(Query query, QuerySolutionMap bindings) {
    return executeInLock(Lock.READ, () -> {
      traceQuery("Ask", query, bindings, null);

      try (QueryExecution queryExecution = QueryExecutionFactory.create(query, dataset, bindings)) {
        return queryExecution.execAsk();
      }
    });

  }

  @Override
  public Model executeConstructQuery(Query query, QuerySolutionMap bindings) {
    return executeInLock(Lock.READ, () -> {
      traceQuery("Construct", query, bindings, null);

      try (QueryExecution queryExecution = QueryExecutionFactory.create(query, dataset, bindings)) {
        return queryExecution.execConstruct();
      }
    });
  }

  @Override
  public void executeUpdateQuery(String updateQuery) {
    executeInLock(Lock.WRITE, () -> {
      UpdateRequest request = UpdateFactory.create(updateQuery);
      UpdateAction.execute(request, dataset);
    });
  }

  @Override
  public void replaceGraph(String graphUri, Model model) {
    executeInLock(Lock.WRITE, () -> {
      dataset.removeNamedModel(graphUri)
             .getNamedModel(graphUri)
             .add(model);
    });
  }

  @Override
  public void deleteGraph(String graphUri) {
    dataset.removeNamedModel(graphUri);
  }

  @Override
  public void delete() {
    dataset.close();
  }

  private void executeInLock(boolean lock, Runnable toExecute) {
    dataset.getLock().enterCriticalSection(lock);
    try {
      toExecute.run();
    }
    finally {
      dataset.getLock().leaveCriticalSection();
    }
  }

  private <T> T executeInLock(boolean lock, Supplier<T> toExecute) {
    dataset.getLock().enterCriticalSection(lock);
    try {
      return toExecute.get();
    }
    finally {
      dataset.getLock().leaveCriticalSection();
    }
  }

  private void traceQuery(String queryType, Query query, QuerySolutionMap bindings, String context) {
    if (log.isTraceEnabled()) log.trace("{} {} - {} \n{}",
                                       queryType,
                                       context == null ? "" : "--- " + context + " --- ",
                                       bindings == null ? "{}" : bindings,
                                       query);
  }

  /**
   * Create and return an in memory dataset copy.
   *
   * @return a copy of current Dataset
   * @see zone.cogni.libs.jena.utils.DatasetHelper#copy(Dataset)
   */
  public Dataset copy() {
    return DatasetHelper.copy(dataset);
  }
}
