package zone.cogni.asquare.transactional;

import java.util.function.Supplier;

public interface ASquareTransaction {

  <T> T readChecked(CheckedSupplier<T> read) throws Throwable;
  <T> T writeChecked(CheckedSupplier<T> write) throws Throwable;

  default <T> T transactChecked(CheckedSupplier<T> t, ASquareTransactionType type) throws Throwable {
    return type == ASquareTransactionType.WRITE ? writeChecked(t) : readChecked(t);
  }

  default <T> T read(Supplier<T> read) {
    try {
      return readChecked(read::get);
    }
    catch (Throwable throwable) {
      if (throwable instanceof RuntimeException) throw (RuntimeException) throwable;
      throw new RuntimeException(throwable);
    }
  }

  default void read(Runnable runnable) {
    read(() -> {
      runnable.run();
      return null;
    });
  }

  default <T> T write(Supplier<T> write) {
    try {
      return writeChecked(write::get);
    }
    catch (Throwable throwable) {
      if (throwable instanceof RuntimeException) throw (RuntimeException) throwable;
      throw new RuntimeException(throwable);
    }
  }

  default void write(Runnable runnable) {
    write(() -> {
      runnable.run();
      return null;
    });
  }

  default <T> T transact(Supplier<T> t, ASquareTransactionType type) {
    return type == ASquareTransactionType.WRITE ? write(t) : read(t);
  }
}
