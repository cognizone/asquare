package zone.cogni.core.util.function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public class CachingSupplier<T> implements Supplier<T> {

  private static final Logger log = LoggerFactory.getLogger(CachingSupplier.class);

  public static <T> Supplier<T> memoize(Supplier<T> valueSupplier) {
    if (valueSupplier instanceof CachingSupplier) return valueSupplier;

    return new CachingSupplier<>(valueSupplier);
  }

  private final Object lock = new Object();
  private final Supplier<T> originalSupplier;
  private volatile T cachedValue;

  private CachingSupplier(Supplier<T> originalSupplier) {
    this.originalSupplier = originalSupplier;
  }

  @Override
  public T get() {
    if (log.isDebugEnabled()) log.debug(".. .. .. In CachingSupplier.get.");

    if (cachedValue == null) {
      synchronized (lock) {
        if (cachedValue == null) {
          if (log.isInfoEnabled()) log.info(".. .. .. .. creating cache");

          cachedValue = originalSupplier.get();

          if (log.isInfoEnabled()) log.info(".. .. .. .. creating cache done");
        }
      }
    }

    if (log.isDebugEnabled()) log.debug(".. .. .. Out CachingSupplier.get.");
    return cachedValue;
  }
}
