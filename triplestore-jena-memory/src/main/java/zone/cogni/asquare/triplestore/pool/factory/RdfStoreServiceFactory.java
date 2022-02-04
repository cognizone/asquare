package zone.cogni.asquare.triplestore.pool.factory;

import org.apache.commons.pool2.KeyedPooledObjectFactory;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.triplestore.pool.PoolableRdfStoreService;
import zone.cogni.asquare.triplestore.pool.key.RdfStoreServicePoolKey;

/**
 * An interface defining life-cycle methods for {@code RdfStoreService}
 * instances to be served by a {@link org.apache.commons.pool2.KeyedObjectPool}.
 * <p>
 * By contract, when an {@link org.apache.commons.pool2.KeyedObjectPool}
 * delegates to a {@link KeyedPooledObjectFactory},
 * </p>
 * <ol>
 *  <li>
 *   {@link #makeObject} is called whenever a new instance is needed.
 *  </li>
 *  <li>
 *   {@link #activateObject} is invoked on every instance that has been
 *   {@link #passivateObject passivated} before it is
 *   {@link org.apache.commons.pool2.KeyedObjectPool#borrowObject borrowed} from the pool.
 *  </li>
 *  <li>
 *   {@link #validateObject} may be invoked on {@link #activateObject activated}
 *   instances to make sure they can be
 *   {@link org.apache.commons.pool2.KeyedObjectPool#borrowObject borrowed} from the pool.
 *   {@code validateObject} may also be used to test an
 *   instance being {@link org.apache.commons.pool2.KeyedObjectPool#returnObject returned} to the pool
 *   before it is {@link #passivateObject passivated}. It will only be invoked
 *   on an activated instance.
 *  </li>
 *  <li>
 *   {@link #passivateObject passivateObject}
 *   is invoked on every instance when it is returned to the pool.
 *  </li>
 *  <li>
 *   {@link #destroyObject destroyObject}
 *   is invoked on every instance when it is being "dropped" from the
 *   pool (whether due to the response from {@code validateObject},
 *   or for reasons specific to the pool implementation.) There is no
 *   guarantee that the instance being destroyed will
 *   be considered active, passive or in a generally consistent state.
 *  </li>
 * </ol>
 *
 * @see org.apache.commons.pool2.KeyedObjectPool
 * @see org.apache.commons.pool2.BaseKeyedPooledObjectFactory
 * @see zone.cogni.asquare.triplestore.pool.key.RdfStoreServicePoolKey
 * @see RdfStoreService
 *
 * @param <K> The type of keys managed by this factory.
 * @param <V> Type of element managed by this factory.
 *
 * @since 0.5.3
 */
public interface RdfStoreServiceFactory<K extends RdfStoreServicePoolKey<?>, V extends PoolableRdfStoreService<K>>
  extends KeyedPooledObjectFactory<K, V> {
}
