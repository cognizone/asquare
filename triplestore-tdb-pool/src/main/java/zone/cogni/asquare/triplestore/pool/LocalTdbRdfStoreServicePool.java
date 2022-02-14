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

  /**
   * Default pool configuration.
   *
   * This configuration object is shared, but it is not thread safe for any kind of changes.
   * It is only here to reuse it in case the caller only wants to change a few options but not all.
   *
   */
  public static final GenericKeyedObjectPoolConfig<PollableLocalTdbRdfStoreService> DEFAULT_CONFIGURATION = new GenericKeyedObjectPoolConfig<>();
  static {
    DEFAULT_CONFIGURATION.setFairness(true);
    DEFAULT_CONFIGURATION.setMaxTotalPerKey(15);
    DEFAULT_CONFIGURATION.setBlockWhenExhausted(true);
    DEFAULT_CONFIGURATION.setTestOnBorrow(true);
    DEFAULT_CONFIGURATION.setTestOnCreate(true);
    DEFAULT_CONFIGURATION.setTestOnReturn(true);
    DEFAULT_CONFIGURATION.setTestWhileIdle(true);
    DEFAULT_CONFIGURATION.setMaxWait(Duration.ofMillis(2000L));
    DEFAULT_CONFIGURATION.setTimeBetweenEvictionRuns(Duration.parse("PT60M"));
  }

  /**
   * Default abandoned configuration.
   *
   * This configuration object is shared, but it is not thread safe for any kind of changes.
   * It is only here to reuse it in case the caller only wants to change a few options but not all.
   *
   */
  public static final AbandonedConfig DEFAULT_ABANDONED_CONFIGURATION = new AbandonedConfig();
  static {
    DEFAULT_ABANDONED_CONFIGURATION.setLogAbandoned(true);
    DEFAULT_ABANDONED_CONFIGURATION.setRequireFullStackTrace(true);
    DEFAULT_ABANDONED_CONFIGURATION.setRemoveAbandonedOnBorrow(true);
    DEFAULT_ABANDONED_CONFIGURATION.setRemoveAbandonedOnMaintenance(true);
    DEFAULT_ABANDONED_CONFIGURATION.setUseUsageTracking(true);
    DEFAULT_ABANDONED_CONFIGURATION.setRemoveAbandonedTimeout(Duration.parse("PT5M"));
  }

  // Set the default configuration
  private static GenericKeyedObjectPoolConfig<PollableLocalTdbRdfStoreService> configuration;
  private static AbandonedConfig abandonedConfiguration;
  private static Float erodingFactor;
  private static Boolean erodingPerKey;

  // Keep both pool instances for later configuration possibilities
  // genericPool is the configurable base pool implementation
  // pool is the final pool instance where we have wrappers around the base genericPool implementation
  private final GenericKeyedObjectPool<LocalTdbPoolKey, PollableLocalTdbRdfStoreService> genericPool;
  private final KeyedObjectPool<LocalTdbPoolKey, PollableLocalTdbRdfStoreService> pool;

  private static class SingletonHolder {
    static {
      if(configuration == null) {
        configuration = DEFAULT_CONFIGURATION;
      }
      if(abandonedConfiguration == null) {
        abandonedConfiguration = DEFAULT_ABANDONED_CONFIGURATION;
      }
      if(erodingFactor == null) {
        erodingFactor = 1.0f;
      }
      if(erodingPerKey == null) {
        erodingPerKey = true;
      }
    }
    public static final LocalTdbRdfStoreServicePool INSTANCE = new LocalTdbRdfStoreServicePool();
  }

  public static LocalTdbRdfStoreServicePool getInstance() {
    return SingletonHolder.INSTANCE;
  }

  /**
   * This implementation does not guarantee thread safety against parallel call of
   * {@code LocalTdbRdfStoreServicePool#configure} and {@code LocalTdbRdfStoreServicePool#getInstance}.
   *
   * This is up to the caller to make sure configuration happens safely before getInstance.
   *
   * Note: this is a hacky solution but without having Spring Singleton Bean technique used here
   *       it's hard to keep the pool as singleton. And this type of singleton pattern makes configuration a bit more difficult.
   *
   * We decorate the pool with eroding pool that adaptively decreases its size when idle objects are
   * no longer needed. This is intended as an always thread-safe alternative
   * to using an idle object evictor provided by many pool implementations.
   * This is also an effective way to shrink FIFO ordered pools that experience load spikes.
   *
   * @param poolCnf
*               a simple "struct" encapsulating the configuration for a {@link GenericKeyedObjectPool}.
   * @param abandonedConfig
   *            configuration settings for abandoned object removal.
   * @param factor
   *            a positive value to scale the rate at which the pool tries to
   *            reduce its size. If 0 &lt; factor &lt; 1 then the pool
   *            shrinks more aggressively. If 1 &lt; factor then the pool
   *            shrinks less aggressively.
   * @param perKey
   *            when true, each key is treated independently in the eroding pool
   *
   * @throws IllegalAccessException when the caller tries to set the configuration more than one time.
   */
  public static synchronized void configure (
    final GenericKeyedObjectPoolConfig<PollableLocalTdbRdfStoreService> poolCnf,
    final AbandonedConfig abandonedConfig,
    final Float factor,
    final Boolean perKey
  ) throws IllegalAccessException {
    // we don't allow multiple configuration calls, pool can be configured only once
    if(
      configuration != null || abandonedConfiguration != null || erodingFactor != null || erodingPerKey != null
    ) {
      throw new IllegalAccessException("Pool configuration can be set only once and then it cannot be modified later.");
    }
    configuration = poolCnf;
    abandonedConfiguration = abandonedConfig;
    erodingFactor = factor;
    erodingPerKey = perKey;
  }

  /**
   * Sets the number of milliseconds to sleep between runs of the idle object evictor thread.
   * <ul>
   * <li>When positive, the idle object evictor thread starts.</li>
   * <li>When non-positive, no idle object evictor thread runs.</li>
   * </ul>
   *
   * (it is not intended to be used in production environments, mainly for testing purposes)
   *
   * @param timeBetweenEvictionRuns   duration to sleep between evictor runs
   */
  public static synchronized void setTimeBetweenEvictionRuns(final Duration timeBetweenEvictionRuns) {
    getInstance().genericPool.setTimeBetweenEvictionRuns(timeBetweenEvictionRuns);
  }

  /**
   * Sets the timeout before an abandoned object can be removed.
   *
   * (it is not intended to be used in production environments, mainly for testing purposes)
   *
   * @param removeAbandonedTimeout new abandoned timeout
   */
  public static synchronized void setRemoveAbandonedTimeout(final Duration removeAbandonedTimeout) {
    abandonedConfiguration.setRemoveAbandonedTimeout(removeAbandonedTimeout);
    getInstance().genericPool.setAbandonedConfig(abandonedConfiguration);
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
    genericPool = new GenericKeyedObjectPool<>(new LocalTdbRdfStoreServiceFactory(), configuration, abandonedConfiguration);

    pool = PoolUtils.erodingPool(
      //TODO:  make ProxiedKeyedObjectPool work and replace genericPool with:
      //       new ProxiedKeyedObjectPool<>(genericPool, new CglibProxySource<>(PollableLocalTdbRdfStoreService.class)),
      //       the underlying problem here is that LocalTdbRdfStoreService configures the TDB from inside the constructor
      //       and CglibProxySource uses Cglib which means it creates the new instance with no-parameter constructor
      //       then copies the data later from the original object that is about to be proxied
      genericPool, erodingFactor, erodingPerKey
    );
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
