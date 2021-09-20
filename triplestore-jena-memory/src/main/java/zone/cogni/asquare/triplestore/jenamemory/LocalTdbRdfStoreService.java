package zone.cogni.asquare.triplestore.jenamemory;


import com.google.common.base.Preconditions;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shared.Lock;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.tdb.StoreConnection;
import org.apache.jena.tdb.TDBFactory;
import org.apache.jena.tdb.base.file.Location;
import org.apache.jena.tdb.setup.StoreParams;
import org.apache.jena.update.UpdateAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.sem.jena.JenaUtils;
import zone.cogni.sem.jena.template.JenaBooleanHandler;
import zone.cogni.sem.jena.template.JenaResultSetHandler;
import zone.cogni.sem.jena.template.JenaTemplate;

import javax.annotation.Nonnull;
import java.io.Closeable;
import java.io.File;
import java.util.Objects;
import java.util.function.Supplier;


public class LocalTdbRdfStoreService implements RdfStoreService, Closeable {

  private static final Logger log = LoggerFactory.getLogger(LocalTdbRdfStoreService.class);

  private final File tdbLocationFolder;
  private final DatasetGraph datasetGraph;
  private final Model model;
  //  private final String storeName;
  //  private String preLoadLocations;
  //  private ResourcePatternResolver resourcePatternResolver;
  private boolean closed;

  public LocalTdbRdfStoreService(File tdbLocationFolder, File initFolder) {
    log.info(".. .. creating TDB store - {}", tdbLocationFolder.getPath());

    this.tdbLocationFolder = tdbLocationFolder;
    datasetGraph = calculateDatasetGraph();
    model = ModelFactory.createModelForGraph(datasetGraph.getDefaultGraph());

    initialize(initFolder);
    log.info(".. .. done creating TDB store");
  }

  private void initialize(File initFolder) {
    if (!model.isEmpty()) return;
    if (initFolder == null) return;

    if (!initFolder.exists() || !initFolder.isDirectory()) return;

    File[] files = initFolder.listFiles();
    if (files == null) return;

    for (File file : files) {
      log.info(".. .. .. reading {}", file.getName());
      model.add(JenaUtils.read(new FileSystemResource(file)));
    }
  }

//  public LocalTdbRdfStoreService(String storeName) {
//    this.storeName = storeName;
//  }

//  public void setPreLoadLocations(String preLoadLocations, ResourcePatternResolver resourcePatternResolver) {
//    this.preLoadLocations = preLoadLocations;
//    this.resourcePatternResolver = resourcePatternResolver;
//  }

//  @PostConstruct
//  public void init() throws Exception {
//    File tdbLocationRootFolder = new File(FileUtils.getTempDirectory(), "qdr_tdb");
//    tdbLocationRootFolder.mkdirs();
//    tdbLocationFolder = new File(tdbLocationRootFolder, UUID.randomUUID().toString());
//
//    log.info("Init TDB store for {} in {}", storeName, tdbLocationFolder);
//
//    datasetGraph = calculateDatasetGraph();
//    model = ModelFactory.createModelForGraph(datasetGraph.getDefaultGraph());
//
//    FileUtils.writeStringToFile(new File(tdbLocationFolder, "qdr_tdb_name.txt"), storeName, StandardCharsets.UTF_8);


//    try {
//      FileUtils.forceDeleteOnExit(tdbLocationFolder);
//    }
//    catch (IOException ignore) {
//    }
//    log.info("Init TDB store for {} done", storeName);


//    if (resourcePatternResolver == null || StringUtils.isBlank(preLoadLocations)) return;
//
//    String updatedPreLoadLocations = extFolderHelperService.updatePreLoadLocations(preLoadLocations);
//    log.info("Loading preLoadLocations -> {}", updatedPreLoadLocations);
//    Arrays.stream(StringUtils.split(updatedPreLoadLocations, ',')).forEach(location -> {
//      log.info("Loading RDF file {}.", location);
//      Arrays.stream(ResourceHelper.getResources(resourcePatternResolver, location)).forEach(resource -> {
//        try (InputStream inputStream = resource.getInputStream()) {
//          model.read(inputStream, null, JenaUtils.getLangByResourceName(location));
//        }
//        catch (IOException e) {
//          throw new RuntimeException(e);
//        }
//      });
//    });
//    log.info("Loading preLoadLocations done");
//  }

  private synchronized DatasetGraph calculateDatasetGraph() {
    Location tdbLocation = Location.create(tdbLocationFolder.getAbsolutePath());
    setupConnection(tdbLocation);
    return TDBFactory.createDatasetGraph(tdbLocation);
  }

  private void setupConnection(@Nonnull Location tdbLocation) {
    StoreConnection connection = StoreConnection.getExisting(tdbLocation);
    if (connection != null) return;

    try {
      TDBFactory.setup(tdbLocation, StoreParams.getSmallStoreParams());
    }
    catch (RuntimeException e) {
      if (!Objects.equals(e.getMessage(), "Location is already active")) {
        throw e;
      }

      log.warn("TDBFactory.setup reported a problem: {}", e.getMessage());

      boolean storeInitialized = StoreConnection.getExisting(tdbLocation) != null;
      if (!storeInitialized) log.error("Problem with initializing store.");

      Preconditions.checkState(storeInitialized, "Store is not initialized!");
    }
  }

  //  @Override
  public Model getModel() {
    return model;
  }

  @Override
  public void close() {
    if (closed) return;
    log.info(".. .. closing TDB - {}", tdbLocationFolder.getPath());
    try {
      if (datasetGraph.isInTransaction()) {
        datasetGraph.abort();
      }
    }
    catch (Exception ignore) {
    }

    try {
      TDBFactory.release(datasetGraph);
    }
    catch (Exception e) {
      log.error(".. .. close TDB - TDBFactory.release for {} failed", tdbLocationFolder.getPath(), e);
    }
//    boolean deleted = FileUtils.deleteQuietly(tdbLocationFolder);
//    if (deleted) {
//      log.info("Close TDB done - {}", storeName);
    closed = true;
//    }
//    else {
    log.warn(".. .. closed TDB - folder not deleted - {}", tdbLocationFolder.getPath());
//    }

  }

  @Override
  public void addData(Model model) {
    executeInLock(Lock.WRITE, () -> this.model.add(model));
  }

  @Override
  public void addData(Model model, String graphUri) {
    throw new RuntimeException("Add data with graph not supported"); //or we add to default graph?
  }

  @Override
  public <R> R executeSelectQuery(Query query, QuerySolutionMap bindings, JenaResultSetHandler<R> resultSetHandler, String context) {
    return executeInLock(Lock.READ, () -> {
      if (log.isTraceEnabled()) log.trace("Select {} - {} \n{}",
                                          context == null ? "" : "--- " + context + " --- ",
                                          bindings,
                                          query);

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
  public <R> R executeSelectQuery(String query, JenaResultSetHandler<R> resultSetHandler, String context) {
    return executeInLock(Lock.READ, () -> {
      if (log.isDebugEnabled()) log.debug("Select {} \n{}", context == null ? "" : "--- " + context + " --- ", query);

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
  public <R> R executeSelectQuery(String query, JenaResultSetHandler<R> resultSetHandler) {
    return executeSelectQuery(query, resultSetHandler, null);
  }

  @Override
  public boolean executeAskQuery(Query query, QuerySolutionMap bindings) {
    return executeInLock(Lock.READ, () -> {
      try (QueryExecution queryExecution = bindings.asMap().isEmpty() ? QueryExecutionFactory.create(query, model)
                                                                      : QueryExecutionFactory.create(query, model, bindings)
      ) {
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
    return executeInLock(Lock.READ, () -> {
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
  public Model executeConstructQuery(String query) {
    return executeInLock(Lock.READ, () -> {
      try (QueryExecution queryExecution = QueryExecutionFactory.create(query, model)) {
        log.debug("Running construct query: \n{}", query);
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
    executeInLock(Lock.WRITE, () -> UpdateAction.parseExecute(updateQuery, model));
  }

  @Override
  public boolean graphExists(String graphUri) {
    return datasetGraph.containsGraph(NodeFactory.createURI(graphUri));
  }

  @Override
  public void delete() {
    executeInLock(Lock.WRITE, () -> model.removeAll());
  }

  //  @Override
  public <R> R executeAskQuery(String query, JenaBooleanHandler<R> jenaBooleanHandler) {
    return executeInLock(Lock.READ, () -> JenaTemplate.ask(model, query, jenaBooleanHandler));
  }

  private void executeInLock(boolean lock, Runnable executeInLock) {
    model.enterCriticalSection(lock);
    try {
      executeInLock.run();
    }
    finally {
      model.leaveCriticalSection();
    }
  }


  private <T> T executeInLock(boolean lock, Supplier<T> executeInLock) {
    model.enterCriticalSection(lock);
    try {
      return executeInLock.get();
    }
    finally {
      model.leaveCriticalSection();
    }
  }
}
