package zone.cogni.asquare.triplestore.pool.key;

@FunctionalInterface
public interface RdfStoreServicePoolKey<T> {
  T key();
}
