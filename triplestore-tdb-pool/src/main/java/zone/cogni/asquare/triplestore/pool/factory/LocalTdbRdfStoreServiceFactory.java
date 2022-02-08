package zone.cogni.asquare.triplestore.pool.factory;

import zone.cogni.asquare.triplestore.pool.jenamemory.PollableLocalTdbRdfStoreService;
import zone.cogni.asquare.triplestore.pool.key.LocalTdbPoolKey;

/**
 * A base implementation of {@code RdfStoreServiceFactory} and {@code KeyedPooledObjectFactory}.
 * <p>
 * This class is immutable, and therefore thread-safe.
 * </p>
 *
 * @see zone.cogni.asquare.triplestore.pool.key.LocalTdbPoolKey
 * @see zone.cogni.asquare.triplestore.jenamemory.LocalTdbRdfStoreService
 * @see zone.cogni.asquare.triplestore.pool.factory.BaseRdfStoreServiceFactory
 * @see RdfStoreServiceFactory
 *
 * @since 0.5.3
 */
public class LocalTdbRdfStoreServiceFactory extends BaseRdfStoreServiceFactory<LocalTdbPoolKey, PollableLocalTdbRdfStoreService> {

  /**
   * Create an instance that can be served by the pool.
   *
   * @param key the key used when constructing the object
   *
   * @return an instance that can be served by the pool
   *
   * @throws Exception if there is a problem creating a new instance,
   *                   this will be propagated to the code requesting an object.
   */
  @Override
  public PollableLocalTdbRdfStoreService create(final LocalTdbPoolKey key) throws Exception {
    return PollableLocalTdbRdfStoreService.createFrom(key);
  }
}
