package zone.cogni.asquare.triplestore.pool.jenamemory;


import org.apache.jena.atlas.RuntimeIOException;
import org.apache.jena.query.TxnType;
import org.apache.jena.tdb.StoreConnection;
import org.apache.jena.tdb.TDBException;
import org.apache.jena.tdb.base.file.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.triplestore.jenamemory.LocalTdbRdfStoreService;
import zone.cogni.asquare.triplestore.pool.PoolableRdfStoreService;
import zone.cogni.asquare.triplestore.pool.key.LocalTdbPoolKey;

import java.io.File;
import java.util.concurrent.TimeUnit;

public class PoolableLocalTdbRdfStoreService
  extends LocalTdbRdfStoreService implements PoolableRdfStoreService<LocalTdbPoolKey> {

  private static final Logger log = LoggerFactory.getLogger(PoolableLocalTdbRdfStoreService.class);

  public static PoolableLocalTdbRdfStoreService createFrom(final LocalTdbPoolKey key) {
    return new PoolableLocalTdbRdfStoreService(key.getDirPath().toFile());
  }

  public PoolableLocalTdbRdfStoreService(File tdbLocationFolder, File initFolder, long firstResultTimeout, TimeUnit firstResultTimeUnit, long overallTimeout, TimeUnit overallTimeUnit) {
    super(tdbLocationFolder, initFolder, firstResultTimeout, firstResultTimeUnit, overallTimeout, overallTimeUnit);
  }

  public PoolableLocalTdbRdfStoreService(File tdbLocationFolder) {
    super(tdbLocationFolder);
  }

  public PoolableLocalTdbRdfStoreService(File tdbLocationFolder, long firstResultTimeout, TimeUnit firstResultTimeUnit) {
    super(tdbLocationFolder, firstResultTimeout, firstResultTimeUnit);
  }

  public PoolableLocalTdbRdfStoreService(File tdbLocationFolder, long firstResultTimeout, TimeUnit firstResultTimeUnit, long overallTimeout, TimeUnit overallTimeUnit) {
    super(tdbLocationFolder, firstResultTimeout, firstResultTimeUnit, overallTimeout, overallTimeUnit);
  }

  public PoolableLocalTdbRdfStoreService(File tdbLocationFolder, File initFolder) {
    super(tdbLocationFolder, initFolder);
  }

  public PoolableLocalTdbRdfStoreService(File tdbLocationFolder, File initFolder, long firstResultTimeout, TimeUnit firstResultTimeUnit) {
    super(tdbLocationFolder, initFolder, firstResultTimeout, firstResultTimeUnit);
  }

  /**
   * Reinitialize an instance to be returned by the pool.
   * <p>
   * The default implementation is a no-op.
   * </p>
   */
  @Override
  public void activateObject() throws Exception {
    if(getDataset().isInTransaction()) {
      getDataset().end();
    }
    getDataset().begin(TxnType.READ_COMMITTED_PROMOTE);
    ready.set(true);
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
      if(getDataset().isInTransaction()) {
        getDataset().commit();
      }
    }
    finally {
      if(getDataset().isInTransaction()) {
        getDataset().end();
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
    return ready.get() && PoolableRdfStoreService.super.validateObject(key) && executeAskQuery("ask {}");
  }

  /**
   * Destroy an instance no longer needed by the pool.
   * <p>
   * The default implementation is to close the RDF store service.
   * </p>
   */
  @Override
  public void destroyObject() throws Exception {
    final StoreConnection connection = StoreConnection.getExisting(Location.create(getTdbLocation()));
    boolean connectionIsValid = true;
    try {
      PoolableRdfStoreService.super.destroyObject();
      connectionIsValid = connection == null || connection.isValid();
    } catch (final TDBException | RuntimeIOException e) {
      log.error("Problem during destroying the {} TDB object: {}.", getTdbLocation(), this, e);
      connectionIsValid = false;
      throw e;
    } finally {
      if(!connectionIsValid) {
        forceRelease();
      }
    }
  }
}
