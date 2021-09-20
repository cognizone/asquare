package zone.cogni.asquare.service.async;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AsyncUtilsTimeoutTest {

  @Test
  public void testWhileLock() {
    AtomicBoolean lock = new AtomicBoolean(true);
    assertTrue(AsyncUtils.timeoutWhileLock(lock, 1000));
    lock.set(false);
    assertFalse(AsyncUtils.timeoutWhileLock(lock, 1000));
  }

  @Test
  public void testWhileUnlock() {
    AtomicBoolean lock = new AtomicBoolean(false);
    assertTrue(AsyncUtils.timeoutWhileUnlock(lock, 1000));
    lock.set(true);
    assertFalse(AsyncUtils.timeoutWhileUnlock(lock, 1000));
  }

  @Test
  public void testWhileDone() {
    AtomicBoolean lock = new AtomicBoolean(true);
    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
      assertFalse(AsyncUtils.timeoutWhileLock(lock, 5000));
      lock.set(false);
    });
    assertFalse(AsyncUtils.timeoutWhileDone(future, 500));
    lock.set(false);
    future.complete(null);
    assertTrue(AsyncUtils.timeoutWhileDone(future, 500));
    assertTrue(future.isDone());
    assertFalse(AsyncUtils.timeoutWhileUndone(future, 100));
  }

}
