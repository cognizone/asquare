package zone.cogni.asquare.triplestore.jenamemory;


import org.apache.jena.atlas.RuntimeIOException;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryCancelledException;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.query.Syntax;
import org.apache.jena.query.TxnType;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.system.Txn;
import org.apache.jena.tdb.StoreConnection;
import org.apache.jena.tdb.TDBFactory;
import org.apache.jena.tdb.base.file.ChannelManager;
import org.apache.jena.tdb.base.file.FileException;
import org.apache.jena.tdb.base.file.Location;
import org.apache.jena.tdb.transaction.TDBTransactionException;
import org.apache.jena.update.UpdateExecutionFactory;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import zone.cogni.asquare.triplestore.pool.PoolableRdfStoreService;
import zone.cogni.asquare.triplestore.pool.key.LocalTdbPoolKey;
import zone.cogni.sem.jena.template.JenaBooleanHandler;
import zone.cogni.sem.jena.template.JenaResultSetHandler;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class LocalTdbRdfStoreService implements PoolableRdfStoreService<LocalTdbPoolKey> {

  private static final Logger log = LoggerFactory.getLogger(LocalTdbRdfStoreService.class);

  public static final long DEFAULT_FIRST_RESULT_TIMEOUT = 10;
  public static final TimeUnit DEFAULT_FIRST_RESULT_TIME_UNIT = TimeUnit.MINUTES;
  public static final long DEFAULT_OVERALL_RESULT_TIMEOUT = 30;
  public static final TimeUnit DEFAULT_OVERALL_RESULT_TIME_UNIT = TimeUnit.MINUTES;

  private final String tdbLocation;
  private final Dataset dataset;

  /**
   * Set timeouts on the query execution; the first result timeout refers to time to first result.
   * Processing will be aborted if a timeout expires.
   * <b>Not all query execution systems support timeouts.</b>
   * A timeout of less than zero means no timeout.
   */
  private final long firstResultTimeout;
  private final TimeUnit firstResultTimeUnit;

  /**
   * Set timeouts on the query execution; the overall timeout refers to overall query execution after the first result.
   * Processing will be aborted if a timeout expires.
   * <b>Not all query execution systems support timeouts.</b>
   * A timeout of less than zero means no timeout.
   */
  private final long overallTimeout;
  private final TimeUnit overallTimeUnit;

  private volatile boolean ready;

  public static LocalTdbRdfStoreService createFrom(final LocalTdbPoolKey key) {
    return new LocalTdbRdfStoreService(key.getDirPath().toFile());
  }

  public LocalTdbRdfStoreService(
    final File tdbLocationFolder, final File initFolder,
    final long firstResultTimeout, final TimeUnit firstResultTimeUnit,
    final long overallTimeout, final TimeUnit overallTimeUnit
  ) {
    this.tdbLocation = tdbLocationFolder.getPath();
    this.firstResultTimeout = firstResultTimeout;
    this.firstResultTimeUnit = firstResultTimeUnit;
    this.overallTimeout = overallTimeout;
    this.overallTimeUnit = overallTimeUnit;
    log.info(
      ".. .. Opening TDB store - {} with default query timeouts, first result: {}{}, after first result: {}{}",
      this.tdbLocation, this.firstResultTimeout, this.firstResultTimeUnit, this.overallTimeout, this.overallTimeUnit
    );

    this.dataset = TDBFactory.createDataset(tdbLocationFolder.getAbsolutePath());
    initialize(initFolder);
    this.ready = true;
    log.info(".. .. Done creating TDB store - {}", tdbLocation);
  }

  public LocalTdbRdfStoreService(final File tdbLocationFolder) {
    this(
      tdbLocationFolder, null, DEFAULT_FIRST_RESULT_TIMEOUT, DEFAULT_FIRST_RESULT_TIME_UNIT,
      DEFAULT_OVERALL_RESULT_TIMEOUT, DEFAULT_OVERALL_RESULT_TIME_UNIT
    );
  }

  public LocalTdbRdfStoreService(
    final File tdbLocationFolder, final long firstResultTimeout, final TimeUnit firstResultTimeUnit
  ) {
    this(
      tdbLocationFolder, null, firstResultTimeout, firstResultTimeUnit,
      DEFAULT_OVERALL_RESULT_TIMEOUT, DEFAULT_OVERALL_RESULT_TIME_UNIT
    );
  }

  public LocalTdbRdfStoreService(
    final File tdbLocationFolder, final long firstResultTimeout, final TimeUnit firstResultTimeUnit,
    final long overallTimeout, final TimeUnit overallTimeUnit
  ) {
    this(
      tdbLocationFolder, null, firstResultTimeout, firstResultTimeUnit, overallTimeout, overallTimeUnit
    );
  }

  public LocalTdbRdfStoreService(final File tdbLocationFolder, final File initFolder) {
    this(
      tdbLocationFolder, initFolder, DEFAULT_FIRST_RESULT_TIMEOUT, DEFAULT_FIRST_RESULT_TIME_UNIT,
      DEFAULT_OVERALL_RESULT_TIMEOUT, DEFAULT_OVERALL_RESULT_TIME_UNIT
    );
  }

  public LocalTdbRdfStoreService(
    final File tdbLocationFolder, final File initFolder, final long firstResultTimeout, final TimeUnit firstResultTimeUnit
  ) {
    this(
      tdbLocationFolder, initFolder, firstResultTimeout, firstResultTimeUnit,
      DEFAULT_OVERALL_RESULT_TIMEOUT, DEFAULT_OVERALL_RESULT_TIME_UNIT
    );
  }

  private void initialize(final File initFolder) {
    if(initFolder == null || !validateInit(initFolder)) {
      return; // normal case, no init is needed
    }

    for (final File file : Objects.requireNonNull(initFolder.listFiles())) {
      log.info(".. .. .. reading {} into {}s", file.getName(), tdbLocation);
      Txn.executeWrite(dataset, () -> RDFDataMgr.read(dataset, file.getAbsolutePath()));
      log.info(".. .. .. TDB is finished to be initialized with {} - TDB: {}", file.getName(), tdbLocation);
    }
  }

  public Dataset getDataset() {
    return dataset;
  }

  public String getTdbLocation() {
    return tdbLocation;
  }

  public long getFirstResultTimeout() {
    return firstResultTimeout;
  }

  public TimeUnit getFirstResultTimeUnit() {
    return firstResultTimeUnit;
  }

  public long getOverallTimeout() {
    return overallTimeout;
  }

  public TimeUnit getOverallTimeUnit() {
    return overallTimeUnit;
  }

  public long size() {
    validateReadiness();
    return Txn.calculateRead(dataset, () -> dataset.getDefaultModel().size());
  }

  public boolean isEmpty() {
    validateReadiness();
    return Txn.calculateRead(dataset, () -> dataset.getDefaultModel().isEmpty());
  }

  @Override
  public boolean graphExists(final String graphUri) {
    validateReadiness();
    return Txn.calculateRead(dataset, () -> dataset.asDatasetGraph().containsGraph(NodeFactory.createURI(graphUri)));
  }

  /**
   * @deprecated use {@link #executeConstructQuery(String)} instead with query: ("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o. }")
   */
  @Deprecated
  public Model getModel() {
    log.error("!!!IMPORTANT!!! Model of {} TDB is leaked! It means a possible memory leak or race condition.", tdbLocation);
    validateReadiness();
    return dataset.getDefaultModel();
  }

  @Override
  public void close() {
    if (!ready) {
      return;
    }
    ready = false;
    log.info(".. .. closing TDB - {}", tdbLocation);
    Txn.exec(dataset, TxnType.READ_COMMITTED_PROMOTE, dataset::close);
    dataset.end();
  }

  @Override
  public void addData(final Model model) {
    validateReadiness();
    Txn.executeWrite(dataset, () -> {
      dataset.getDefaultModel().add(model);
      dataset.commit();
    });
  }

  /**
   * @deprecated on this type of RdfStoreService, use {@link #addData(Model)} instead.
   */
  @Deprecated
  @Override
  public void addData(final Model model, final String graphUri) {
    throw new UnsupportedOperationException("Add data with graph not supported");
  }

  @Override
  public void executeUpdateQuery(final String updateQuery) {
    validateReadiness();
    Txn.executeWrite(dataset, () -> {
      final UpdateRequest request = UpdateFactory.create(updateQuery, Syntax.syntaxARQ);
      UpdateExecutionFactory.create(request, dataset).execute();
      dataset.commit();
    });
  }

  @Override
  public void delete() {
    validateReadiness();
    Txn.executeWrite(dataset, () -> {
      dataset.getDefaultModel().removeAll();
      dataset.commit();
    });
  }

  @Override
  public <R> R executeSelectQuery(
    final Query query, final QuerySolutionMap bindings, final JenaResultSetHandler<R> resultSetHandler, final String context
  ) {
    if (log.isDebugEnabled()) {
      log.debug("Select {} - {} \n{}", context == null ? "" : "--- " + context + " --- ", bindings, query);
    }
    return callQueryExecution(() -> createQueryExecution(query, bindings), resultSetHandler, context);
  }

  @Override
  public <R> R executeSelectQuery(final String query, final JenaResultSetHandler<R> resultSetHandler, final String context) {
    if (log.isDebugEnabled()) {
      log.debug("Select {} \n{}", context == null ? "" : "--- " + context + " --- ", query);
    }
    return callQueryExecution(() -> createQueryExecution(query), resultSetHandler, context);
  }

  @Override
  public boolean executeAskQuery(final Query query, final QuerySolutionMap bindings) {
    validateReadiness();
    return Txn.calculateRead(dataset, () -> {
      try (final QueryExecution queryExecution = createQueryExecution(query, bindings)) {
        return safeQuery(queryExecution::execAsk, queryExecution);
      }
    });
  }

  @Override
  public Model executeConstructQuery(final Query query, final QuerySolutionMap bindings) {
    if (log.isDebugEnabled()) {
      log.debug("Running construct query: \n{}\nbindings: {}", query, bindings);
    }
    return callQueryConstruct(() -> createQueryExecution(query, bindings));
  }

  @Override
  public Model executeConstructQuery(final String query) {
    if (log.isDebugEnabled()) {
      log.debug("Running construct query: \n{}", query);
    }
    return callQueryConstruct(() -> createQueryExecution(query));
  }

  public Model constructAllTriples() {
    return executeConstructQuery("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o. }");
  }

  public <R> R executeAskQuery(final String query, final JenaBooleanHandler<R> jenaBooleanHandler) {
    return jenaBooleanHandler.handle(executeAskQuery(query));
  }

  /**
   * It releases forcefully the {@code StoreConnection}
   * and that means all local TDB base {@code Dataset} instances will loose their connections to the TDB.
   */
  public void forceRelease() {
    close();
    try{
      StoreConnection.expel(Location.create(tdbLocation), true);
    }
    catch (final FileException | RuntimeIOException e) {
      ChannelManager.release(tdbLocation.concat("/journal.jrnl")); // default transaction file name for TDB1
    }
  }

  /**
   * Reinitialize an instance to be returned by the pool.
   * <p>
   * The default implementation is a no-op.
   * </p>
   */
  @Override
  public void activateObject() throws Exception {
    if(dataset.isInTransaction()) {
      dataset.end();
    }
    dataset.begin(TxnType.READ_COMMITTED_PROMOTE);
    ready = true;
  }

  /**
   * Uninitialize an instance to be returned to the idle object pool.
   * <p>
   * The default implementation is a no-op.
   * </p>
   */
  @Override
  public void passivateObject() {
    try{
      if(dataset.isInTransaction()) {
        dataset.commit();
      }
    }
    finally {
      if(dataset.isInTransaction()) {
        dataset.end();
      }
    }
  }

  /**
   * Ensures that the instance is safe to be returned by the pool.
   * <p>
   * The default implementation always returns {@code true}.
   * </p>
   *
   * @param key
   *
   * @return always {@code true} in the default implementation
   */
  @Override
  public boolean validateObject(final LocalTdbPoolKey key) {
    return ready && PoolableRdfStoreService.super.validateObject(key) && executeAskQuery("ask {}");
  }

  private <R> R callQueryExecution(
    final Supplier<QueryExecution> queryExecutionSupplier,
    final JenaResultSetHandler<R> resultSetHandler,
    final String context
  ) {
    if(StringUtils.hasLength(context)) {
      log.warn("Context is not supported on this type of RdfStoreService but one was provided: {}", context);
    }
    return callQueryExecution(queryExecutionSupplier, resultSetHandler);
  }

  private <R> R callQueryExecution(
    final Supplier<QueryExecution> queryExecutionSupplier, final JenaResultSetHandler<R> resultSetHandler
  ) {
    validateReadiness();
    return Txn.calculateRead(dataset, () -> {
      try (final QueryExecution queryExecution = queryExecutionSupplier.get()) {
        return safeQuery(() -> resultSetHandler.handle(queryExecution.execSelect()), queryExecution);
      }
    });
  }

  private Model callQueryConstruct(final Supplier<QueryExecution> queryExecutionSupplier) {
    validateReadiness();
    return Txn.calculateRead(dataset, () -> {
      try (final QueryExecution queryExecution = queryExecutionSupplier.get()) {
        return safeQuery(queryExecution::execConstruct, queryExecution);
      }
    });
  }

  private void validateReadiness() {
    if(!ready) {
      throw new InvalidRdfStoreServiceStateException();
    }
  }

  private boolean validateInit(final File initFolder) {
    boolean isValid = true;
    final StringBuilder causesMsg = new StringBuilder();

    log.info(".. .. Initialize TDB store - {} with data from: {}", tdbLocation, initFolder);
    if(!initFolder.exists()) {
      causesMsg.append("        * init folder does not exist\n");
      isValid = false;
    }
    if(!initFolder.isDirectory()) {
      causesMsg.append("        * init path is not a folder\n");
      isValid = false;
    }
    if (!dataset.getDefaultModel().isEmpty()) {
      causesMsg.append("        * store is not empty\n");
      isValid = false;
    }

    // null if this abstract pathname does not denote a directory, or if an I/O error occurs.
    if (initFolder.listFiles() == null) {
      causesMsg.append("        * init directory is empty empty\n");
      isValid = false;
    }

    if(!isValid) {
      log.warn(".. .. Initialization failed for TDB store - {} because:\n{}", tdbLocation, causesMsg);
    }

    return isValid;
  }

  public QueryExecution createQueryExecution(final String query) {
    final QueryExecution queryExecution = QueryExecutionFactory.create(query, dataset);
    queryExecution.setTimeout(firstResultTimeout, firstResultTimeUnit, overallTimeout, overallTimeUnit);
    return queryExecution;
  }

  public QueryExecution createQueryExecution(final Query query) {
    return createQueryExecution(query, null);
  }

  public QueryExecution createQueryExecution(final Query query, final QuerySolutionMap bindings) {
    final QueryExecution queryExecution = bindings == null || bindings.asMap().isEmpty()
      ? QueryExecutionFactory.create(query, dataset)
      : QueryExecutionFactory.create(query, dataset, bindings);

    queryExecution.setTimeout(firstResultTimeout, firstResultTimeUnit, overallTimeout, overallTimeUnit);
    return queryExecution;
  }

  private <T> T safeQuery(final Supplier<T> supplier, final QueryExecution queryExecution) {
    try {
      return supplier.get();
    }
    catch (final QueryCancelledException exception) {
      log.error("Query timed out: {}", queryExecution.getQuery());
      queryExecution.abort();
      throw exception;
    }
    catch(final TDBTransactionException | RuntimeIOException exception) {
      ready = false;
      log.error("Query failed: {}", queryExecution.getQuery());
      throw exception;
    }
  }
}
