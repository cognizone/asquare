package zone.cogni.asquare.triplestore.pool;

import org.apache.commons.pool2.KeyedObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import zone.cogni.asquare.triplestore.pool.key.RdfStoreServicePoolKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * This should be one instance per thread and stores all TDBs connections that a given thread used.
 * One connection per TDB but one thread can use more TDBs.
 * <p>
 * <b>Important</b>:  Should be initialized as @RequestScope bean on the given application!
 * </p>
 * <p>
 * Example of use:
 * </p>
 * <pre style="border:solid thin; padding: 1ex;"
 * > @RequestScope ->
 * <code style="color:#0C0">@Bean</code>
 * <code style="color:#0C0">@RequestScope</code>
 * RdfStoreServiceProvider&lt;...> rdfStoreServiceProvider() {
 *   return new RdfStoreServiceProvider<>(...);
 * }</pre>
 *
 * @param <K>
 * @param <V>
 */
public class RdfStoreServiceProvider<K extends RdfStoreServicePoolKey<?>, V extends PoolableRdfStoreService<K>> implements DisposableBean {

  private static final Logger log = LoggerFactory.getLogger(RdfStoreServiceProvider.class);

  private final KeyedObjectPool<K, V> pool;
  private final Map<K, V> cache = new HashMap<>();

  public RdfStoreServiceProvider(final KeyedObjectPool<K, V> pool) {
    this.pool = pool;
  }

  public <R> R call(final K key, final Function<Optional<V>, R> function) throws Exception {
    boolean isInvalidated = false;
    try {
      return function.apply(getStore(key));
    }
    catch (final Exception e) {
      try {
        invalidate(key);
      }
      finally {
        // do not return the object to the pool twice
        isInvalidated = true;
      }
      throw e;
    }
    finally {
      // make sure the object is returned to the pool
      if (!isInvalidated) {
        returnObject(key);
      }
    }
  }

  public Optional<V> getStore(final K key) {
    return Optional.ofNullable(cache.computeIfAbsent(key, k -> {
      try {
        return pool.borrowObject(k);
      }
      catch (final Exception e) {
        log.error("Cannot borrow object from pool: {}", pool, e);
        return null;
      }
    }));
  }

  public void invalidate(final K key) {
    Optional.ofNullable(cache.get(key)).ifPresent(store -> {
      try {
        pool.invalidateObject(key, store);
      }
      catch (final Exception e) {
        log.error("Cannot invalidate object ({}) for pool: {}", store, pool, e);
      }
    });
    cache.remove(key);
  }

  public void returnObject(final K key) {
    Optional.ofNullable(cache.get(key)).ifPresent(store -> {
      try {
        pool.returnObject(key, store);
      }
      catch (final Exception e) {
        log.error("Cannot return object ({}) for pool: {}", store, pool, e);
      }
    });
    cache.remove(key);
  }

  /**
   * Invoked by the containing {@code BeanFactory} on destruction of a bean.
   *
   * @throws Exception in case of shutdown errors. Exceptions will get logged
   *                   but not rethrown to allow other beans to release their resources as well.
   */
  @Override
  public void destroy() throws Exception {
    cache.forEach((key, store) -> {
      if(store != null) {
        try {
          pool.returnObject(key, store);
          log.debug("Returned object ({}) to pool: {}", store, pool);
        }
        catch (final Exception e) {
          log.error("Cannot return object ({}) to pool: {}", store, pool, e);
        }
      }
    });
    cache.clear();
  }
}
