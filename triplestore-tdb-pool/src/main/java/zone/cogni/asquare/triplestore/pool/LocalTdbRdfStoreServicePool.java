package zone.cogni.asquare.triplestore.pool;

import org.apache.commons.pool2.KeyedObjectPool;
import org.apache.commons.pool2.PoolUtils;
import org.apache.commons.pool2.impl.AbandonedConfig;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.triplestore.pool.factory.LocalTdbRdfStoreServiceFactory;
import zone.cogni.asquare.triplestore.pool.jenamemory.PollableLocalTdbRdfStoreService;
import zone.cogni.asquare.triplestore.pool.key.LocalTdbPoolKey;

import java.io.Closeable;
import java.time.Duration;

public final class LocalTdbRdfStoreServicePool implements Closeable {

  private static final Logger log = LoggerFactory.getLogger(LocalTdbRdfStoreServicePool.class);

  private final GenericKeyedObjectPool<LocalTdbPoolKey, PollableLocalTdbRdfStoreService> genericPool;
  private final KeyedObjectPool<LocalTdbPoolKey, PollableLocalTdbRdfStoreService> pool;
  private final AbandonedConfig abandonedConfig;

  private static class SingletonHolder {
    public static final LocalTdbRdfStoreServicePool INSTANCE = new LocalTdbRdfStoreServicePool();
  }

  public static LocalTdbRdfStoreServicePool getInstance() {
    return SingletonHolder.INSTANCE;
  }

  /**
   * Sets the number of milliseconds to sleep between runs of the idle object evictor thread.
   * <ul>
   * <li>When positive, the idle object evictor thread starts.</li>
   * <li>When non-positive, no idle object evictor thread runs.</li>
   * </ul>
   *
   * (mainly for testing purposes)
   *
   * @param timeBetweenEvictionRuns   duration to sleep between evictor runs
   */
  public static synchronized void setTimeBetweenEvictionRuns(final Duration timeBetweenEvictionRuns) {
    getInstance().genericPool.setTimeBetweenEvictionRuns(timeBetweenEvictionRuns);
  }


  /**
   * Sets the timeout before an abandoned object can be removed.
   *
   * (mainly for testing purposes)
   *
   * @param removeAbandonedTimeout new abandoned timeout
   */
  public static synchronized void setRemoveAbandonedTimeout(final Duration removeAbandonedTimeout) {
    getInstance().abandonedConfig.setRemoveAbandonedTimeout(removeAbandonedTimeout);
    getInstance().genericPool.setAbandonedConfig(getInstance().abandonedConfig);
  }

  /**
   * Clears the pool, removing all pooled instances (optional operation).
   *
   * @throws UnsupportedOperationException when this implementation doesn't
   *                                       support the operation
   *
   * @throws Exception if the pool cannot be cleared
   */
  public void clear() throws Exception {
    getPool().clear();
  }

  /**
   * Clears the specified pool, removing all pooled instances corresponding to
   * the given {@code key} (optional operation).
   *
   * @param key the key to clear
   *
   * @throws UnsupportedOperationException when this implementation doesn't
   *                                       support the operation
   *
   * @throws Exception if the key cannot be cleared
   */
  public void clear(final LocalTdbPoolKey key) throws Exception {
    getPool().clear(key);
  }

  /**
   * Closes this pool, and free any resources associated with it.
   * <p>
   * Calling {@code  pool.addObject(addObject)} or
   * {@code  pool.borrowObject(borrowObject)} after invoking this method on a pool
   * will cause them to throw an {@link IllegalStateException}.
   * </p>
   * <p>
   * Implementations should silently fail if not all resources can be freed.
   * </p>
   */
  @Override
  public void close() {
    getPool().close();
  }

  private LocalTdbRdfStoreServicePool() {

    // TODO: move it out to be flexible configuration
    final GenericKeyedObjectPoolConfig<PollableLocalTdbRdfStoreService> poolCnf = new GenericKeyedObjectPoolConfig<>();
    poolCnf.setFairness(true);
    poolCnf.setMaxTotalPerKey(15);
    poolCnf.setBlockWhenExhausted(true);
    poolCnf.setTestOnBorrow(true);
    poolCnf.setTestOnCreate(true);
    poolCnf.setTestOnReturn(true);
    poolCnf.setTestWhileIdle(true);
    poolCnf.setMaxWait(Duration.ofMillis(2000L));
    poolCnf.setTimeBetweenEvictionRuns(Duration.parse("PT60M"));

    abandonedConfig = new AbandonedConfig();
    abandonedConfig.setLogAbandoned(true);
    abandonedConfig.setRequireFullStackTrace(true);
    abandonedConfig.setRemoveAbandonedOnBorrow(true);
    abandonedConfig.setRemoveAbandonedOnMaintenance(true);
    abandonedConfig.setUseUsageTracking(true);
    abandonedConfig.setRemoveAbandonedTimeout(Duration.parse("PT5M"));

    genericPool = new GenericKeyedObjectPool<>(new LocalTdbRdfStoreServiceFactory(), poolCnf, abandonedConfig);

    //TODO: add genericPool.listAllObjects() to spring boot actuator

    //TODO:  make ProxiedKeyedObjectPool work
    // pool = new ProxiedKeyedObjectPool<>(genericPool, new CglibProxySource<>(LocalTdbRdfStoreService.class));

    pool = PoolUtils.erodingPool(genericPool, 1f, true);
    genericPool.setSwallowedExceptionListener(e -> log.error("Swallowed exception from pool ({})", pool, e));

    log.info("LocalTdbRdfStoreServicePool is created: {}", pool);
  }

  /**
   *
   * @return the org.apache.commons.pool.KeyedObjectPool class
   */
  public KeyedObjectPool<LocalTdbPoolKey, PollableLocalTdbRdfStoreService> getPool() {
    return pool;
  }
}
