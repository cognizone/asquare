package zone.cogni.asquare.triplestore.tdb2;


import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.tdb2.DatabaseMgr;
import org.apache.jena.tdb2.TDB2Factory;
import org.apache.jena.update.UpdateAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.sem.jena.JenaUtils;
import zone.cogni.sem.jena.template.JenaResultSetHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.File;

public class Tdb2StoreService implements RdfStoreService, Closeable {

  private static final Logger log = LoggerFactory.getLogger(Tdb2StoreService.class);

  private final File tdbLocationFolder;
  private final Dataset dataset;
  private final Model model;
  private final Tdb2Transaction transaction;
  private boolean closed;


  public static Tdb2StoreService connect(@Nonnull File tdbLocationFolder, @Nullable File initFolder) {
    log.info(".. .. creating TDB store - {}", tdbLocationFolder.getPath());

    Dataset dataset = TDB2Factory.connectDataset(tdbLocationFolder.getAbsolutePath());

    Tdb2StoreService store = new Tdb2StoreService(tdbLocationFolder, dataset);
    store.initializeData(initFolder);

    log.info(".. .. done creating TDB store" );
    return store;
  }

  public static Tdb2StoreService inMemory(@Nullable File initFolder) {
    log.info(".. .. creating in memory TDB store" );

    Dataset dataset = TDB2Factory.createDataset();

    Tdb2StoreService store = new Tdb2StoreService(null, dataset);
    store.initializeData(initFolder);

    log.info(".. .. done creating TDB store" );
    return store;
  }

  public Tdb2StoreService(File tdbLocationFolder, Dataset dataset) {
    this.tdbLocationFolder = tdbLocationFolder;
    this.dataset = dataset;
    model = dataset.getDefaultModel();
    transaction = new Tdb2Transaction(dataset);
  }

  private void initializeData(File initFolder) {
    if (initFolder == null) return;
    if (!initFolder.exists() || !initFolder.isDirectory()) return;

    dataset.begin(ReadWrite.WRITE);
    try {
      File[] files = initFolder.listFiles();
      if (files == null) return;

      for (File file : files) {
        log.info(".. .. .. reading {}", file.getName());
        model.add(JenaUtils.read(new FileSystemResource(file)));
      }
    }
    catch (Throwable e) {
      dataset.abort();
      return;
    }

    dataset.commit();
  }

  @Override
  public void close() {
    if (closed) return;
    log.info(".. .. closing TDB - {}", tdbLocationFolder.getPath());
    try {
      if (dataset.isInTransaction()) {
        dataset.abort();
      }
    }
    catch (Exception ignore) {
    }

    try {
      dataset.close();
    }
    catch (Exception e) {
      log.error(".. .. close TDB2 - dataset.close() for {} failed", tdbLocationFolder.getPath(), e);
    }
    closed = true;
  }

  @Override
  public void addData(Model model) {
    transaction.write(() -> this.model.add(model));
  }

  @Override
  public void addData(Model model, String graphUri) {
    throw new RuntimeException("Add data with graph not supported" ); //or we add to default graph?
  }


  @Override
  public <R> R executeSelectQuery(Query query, QuerySolutionMap bindings, JenaResultSetHandler<R> resultSetHandler, String context) {
    if (log.isTraceEnabled()) log.trace("Select {} - {} \n{}",
                                        context == null ? "" : "--- " + context + " --- ",
                                        bindings,
                                        query);
    return transaction.read(() -> {
      try (QueryExecution queryExecution = QueryExecutionFactory.create(query, model)) {
        ResultSet resultSet = queryExecution.execSelect();
        return resultSetHandler.handle(resultSet);
      }
      catch (RuntimeException e) {
        log.error("Query failed: {}", query);
        throw e;
      }
    });
  }

  @Override
  public boolean executeAskQuery(Query query, QuerySolutionMap bindings) {
    return transaction.read(() -> {
      try (QueryExecution queryExecution = bindings.asMap().isEmpty()
                                           ? QueryExecutionFactory.create(query, model)
                                           : QueryExecutionFactory.create(query, model, bindings)) {
        return queryExecution.execAsk();
      }
      catch (RuntimeException e) {
        log.error("Query failed: {}", query);
        throw e;
      }
    });
  }

  @Override
  public Model executeConstructQuery(Query query, QuerySolutionMap bindings) {
    return transaction.read(() -> {
      try (QueryExecution queryExecution = QueryExecutionFactory.create(query, model, bindings)) {
        if (log.isTraceEnabled()) log.trace("Running construct query: \n{}", query);
        return queryExecution.execConstruct();
      }
      catch (RuntimeException e) {
        log.error("Query failed: {}", query);
        throw e;
      }
    });
  }


  @Override
  public void executeUpdateQuery(String updateQuery) {
    transaction.write(() -> {
      UpdateAction.parseExecute(updateQuery, model);
    });
  }


  @Override
  public void delete() {
    transaction.write((Runnable) model::removeAll);
  }

  public Dataset getDataset() {
    return dataset;
  }

  public void compact() {
    DatabaseMgr.compact(dataset.asDatasetGraph());
  }

  public Tdb2Transaction getTransaction() {
    return transaction;
  }
}

