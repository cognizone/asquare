package zone.cogni.asquare.transactional;

@FunctionalInterface
public interface CheckedSupplier<T> {
  T get() throws Throwable;
}
