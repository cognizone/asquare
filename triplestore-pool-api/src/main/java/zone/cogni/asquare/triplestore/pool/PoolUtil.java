package zone.cogni.asquare.triplestore.pool;

import org.apache.commons.pool2.KeyedObjectPool;
import zone.cogni.asquare.triplestore.pool.key.RdfStoreServicePoolKey;

import java.util.function.Function;

/**
 * Only use it in non-web context aware cases or where you don't have a borrowed object per thread.
 */
public final class PoolUtil {

  public static <K extends RdfStoreServicePoolKey<?>, V extends PoolableRdfStoreService<K>, R> R safeCall(
    final KeyedObjectPool<K, V> pool, final K key, final Function<V, R> function
  ) throws Exception {
    V storeService = null;
    try {
      storeService = pool.borrowObject(key);
      return function.apply(storeService);
    } catch (final Exception e) {
      try {
        pool.invalidateObject(key, storeService);
      }
      finally {
        // do not return the object to the pool twice
        storeService = null;
      }
      throw e;
    } finally {
      // make sure the object is returned to the pool
      if (null != storeService) {
        pool.returnObject(key, storeService);
      }
    }
  }

  private PoolUtil() {
    throw new AssertionError("Should not be initialized!");
  }
}
