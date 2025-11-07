package zone.cogni.asquare.triplestore.pool.factory;

import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.PooledSoftReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.triplestore.pool.PoolableRdfStoreService;
import zone.cogni.asquare.triplestore.pool.key.RdfStoreServicePoolKey;

import java.lang.ref.SoftReference;

/**
 * A base implementation of {@code RdfStoreServiceFactory} and {@code KeyedPooledObjectFactory}.
 * <p>
 * This class is immutable, and therefore thread-safe.
 * </p>
 *
 * @see org.apache.commons.pool2.KeyedPooledObjectFactory
 * @see RdfStoreServiceFactory
 *
 * @param <K> The type of keys managed by this factory.
 * @param <V> Type of element managed by this factory.
 *
 * @since 0.5.3
 */
abstract class BaseRdfStoreServiceFactory<K extends RdfStoreServicePoolKey<?>, V extends PoolableRdfStoreService<K>>
  extends BaseKeyedPooledObjectFactory<K, V>
  implements RdfStoreServiceFactory<K,V>
{

  private static final Logger log = LoggerFactory.getLogger(BaseRdfStoreServiceFactory.class);

  /**
   * Reinitialize an instance to be returned by the pool.
   * <p>
   * The default implementation is a no-op.
   * </p>
   *
   * @param key the key used when selecting the object
   * @param pooledObject   a {@code PooledObject} wrapping the instance to be activated
   */
  @Override
  public void activateObject(final K key, final PooledObject<V> pooledObject) throws Exception {
    try {
      super.activateObject(key, pooledObject);
      pooledObject.getObject().activateObject();
    }
    catch (final Exception e) {
      pooledObject.invalidate();
      throw e;
    }
  }

  /**
   * Destroy an instance no longer needed by the pool.
   * <p>
   * The default implementation is a no-op.
   * </p>
   *
   * @param key the key used when selecting the instance
   * @param pooledObject   a {@code PooledObject} wrapping the instance to be destroyed
   */
  @Override
  public void destroyObject(final K key, final PooledObject<V> pooledObject) throws Exception {
    log.info("Destroy object of {}: {}", key, pooledObject.getObject());
    super.destroyObject(key, pooledObject);
    pooledObject.getObject().destroyObject();
  }

  /**
   * Uninitialize an instance to be returned to the idle object pool.
   *
   * @param key the key used when selecting the object
   * @param pooledObject   a {@code PooledObject} wrapping the instance to be passivated
   */
  @Override
  public void passivateObject(final K key, final PooledObject<V> pooledObject) throws Exception {
    try {
      super.passivateObject(key, pooledObject);
      pooledObject.getObject().passivateObject();
    }
    catch (final Exception e) {
      pooledObject.invalidate();
      throw e;
    }
  }

  /**
   * Ensures that the instance is safe to be returned by the pool.
   *
   * @param key the key used when selecting the object
   * @param pooledObject   a {@code PooledObject} wrapping the instance to be validated
   *
   * @return always {@code true} in the default implementation
   */
  @Override
  public boolean validateObject(final K key, final PooledObject<V> pooledObject) {
    try {
      return super.validateObject(key, pooledObject) && pooledObject.getObject().validateObject(key);
    }
    catch (final Exception e) {
      log.warn("Exception occurred during validation of pooled object: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Wrap the provided instance with an implementation of
   * {@link org.apache.commons.pool2.PooledObject}.
   *
   * @param value the instance to wrap
   *
   * @return The provided instance, wrapped by a {@link org.apache.commons.pool2.PooledObject}
   */
  @Override
  public PooledObject<V> wrap(final V value) {
    return new PooledSoftReference<>(new SoftReference<>(value));
  }
}
