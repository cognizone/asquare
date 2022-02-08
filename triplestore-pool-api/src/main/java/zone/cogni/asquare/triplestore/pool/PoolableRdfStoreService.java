package zone.cogni.asquare.triplestore.pool;

import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.triplestore.pool.key.RdfStoreServicePoolKey;

public interface PoolableRdfStoreService<K extends RdfStoreServicePoolKey<?>> extends RdfStoreService {

  /**
   * Reinitialize an instance to be returned by the pool.
   * <p>
   * The default implementation is a no-op.
   * </p>
   */
  default void activateObject() throws Exception {
    // The default implementation is a no-op.
  }

  /**
   * Destroy an instance no longer needed by the pool.
   * <p>
   * The default implementation is to close the RDF store service.
   * </p>
   */
  default void destroyObject() throws Exception {
    close();
  }

  /**
   * Uninitialize an instance to be returned to the idle object pool.
   * <p>
   * The default implementation is a no-op.
   * </p>
   */
  default void passivateObject() throws Exception {
    // The default implementation is a no-op.
  }

  /**
   * Ensures that the instance is safe to be returned by the pool.
   * <p>
   * The default implementation always returns {@code true}.
   * </p>
   *
   * @return always {@code true} in the default implementation
   */
  default boolean validateObject(K key) {
    return true;
  }
}
