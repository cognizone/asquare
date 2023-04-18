package zone.cogni.asquare.service.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AsyncTaskManager extends ThreadPoolTaskExecutor {

  private static final long serialVersionUID = -1059950250082758143L;

  private static final Logger log = LoggerFactory.getLogger(AsyncTaskManager.class);

  private static final String DEFAULT_KEY = "async-execution-key";

  private static final ThreadLocal<CompletableFuture<Object>> threadLocalCompletableFuture = new ThreadLocal<>();
  private final Map<Object, CompletableFuture<Object>> executionMap = new ConcurrentHashMap<>();
  private final Map<Object, LocalDateTime> executionTime = new ConcurrentHashMap<>();

  private LocalDateTime lastExecutionStart;
  private LocalDateTime lastExecutionStop;

  public AsyncTaskManager() {
    this("default task executor");
  }

  public AsyncTaskManager(String name) {
    this(name, asyncContext -> DEFAULT_KEY, (cfuture, asyncParams) -> {
    });
  }

  public AsyncTaskManager(String name, final BiConsumer<CompletableFuture<Object>, Map<String, Object>> onQueueFn) {
    this(name, asyncContext -> DEFAULT_KEY, onQueueFn);
  }

  public AsyncTaskManager(String name, final Function<Map<String, Object>, Object> asyncKeyFn) {
    this(name, asyncKeyFn, (cfuture, asyncParams) -> {
    });
  }

  public AsyncTaskManager(String name, final Function<Map<String, Object>, Object> asyncKeyFn,
                          final BiConsumer<CompletableFuture<Object>, Map<String, Object>> onQueueFn) {

    setThreadNamePrefix("async-[" + name + "]-");

    setWaitForTasksToCompleteOnShutdown(false);

    setTaskDecorator(
      runnable -> {
        final CompletableFuture<Object> cfuture = new CompletableFuture<>();

        Map<String, Object> asyncContext = AsyncUtils.getAsyncContext(runnable);
        final Object asyncKey = asyncKeyFn.apply(asyncContext);

        log.info(
          "Adding async task {} with async context {} to queue for an execution in {}. Current execution queue: {}",
          asyncKey, asyncContext, name, executionMap.keySet().toArray());

        synchronized (this) {
          if (executionMap.containsKey(asyncKey)) {
            throw new DuplicatedAsyncTaskException("Async task with key {} is already executing", asyncKey);
          }
          executionMap.put(asyncKey, cfuture);
        }

        log.info("Async task {} added to queue for an execution in {}. Current execution queue: {}", asyncKey, name,
                 executionMap.keySet().toArray());

        onQueueFn.accept(cfuture, asyncContext);

        return new AsyncRunnable(
          runnable,
          asyncKey,
          this::setLastExecutionStart,
          this::setLastExecutionStop,
          cfuture,
          executionMap,
          executionTime);
      }
    );

  }

  protected static CompletableFuture<Object> getCompletableFuture() {
    return threadLocalCompletableFuture.get();
  }

  public boolean isBusy() {
    return executionMap.size() > 0;
  }

  public LocalDateTime getLastExecutionStart() {
    return lastExecutionStart;
  }

  public void setLastExecutionStart(LocalDateTime lastExecutionStart) {
    this.lastExecutionStart = lastExecutionStart;
  }

  public LocalDateTime getLastExecutionStop() {
    return lastExecutionStop;
  }

  public void setLastExecutionStop(LocalDateTime lastExecutionStop) {
    this.lastExecutionStop = lastExecutionStop;
  }

  public boolean isMoreBusyThan(int numberOfExecutions) {
    return executionMap.size() > numberOfExecutions;
  }

  public boolean isBusyWith(Object key) {
    return executionMap.containsKey(key);
  }

  public void awaitPoolIsReady() {
    awaitBusyWithNotMore(getCorePoolSize() - 1);
  }

  /**
   * Wait until workers pool has requested amount of free slots.
   * Force terminating threads when when activeCnt = 0
   * @param activeCnt - amount of remaining active workers in pool
   * @param timeoutms - timeout when force interrupting threads, only works when activeCnt = 0
   * @return list of interrupted workers
   */
  public List<String> awaitBusyWithNotMoreAndNoLongerThan(int activeCnt, long timeoutms) {
    List<String> interruptedKeys = new ArrayList<>();
    long start = System.currentTimeMillis();
    while (isMoreBusyThan(activeCnt)) {
      AsyncUtils.timeoutWhile(500, ()->true);

      if (activeCnt == 0 && timeoutms != -1 && System.currentTimeMillis() - start > timeoutms) {
        List<String> executionKeys = getExecutionKeysAsStrings();

        for (String execution : executionKeys) {
          log.error("Force interrupting process {} after timeout {} ms", execution, timeoutms);

          try {
            CompletableFuture<Object> completable = findCompletableFuture(execution);
            completable.cancel(true);
          }
          catch (Exception ex) {
            log.error("Exception when reporting that thread will be interrupted {}", execution, ex);
          }
          executionMap.remove(execution);
          interruptedKeys.add(execution);
        }

        shutdown();
        initialize();

        break;
      }
    }
    return interruptedKeys;
  }

  public void awaitBusyWithNotMore(int activeCnt) {
    awaitBusyWithNotMoreAndNoLongerThan(activeCnt, -1);
  }

  public List<String> getExecutionKeysAsStrings() {
    return executionMap.keySet().stream().map(Object::toString).collect(Collectors.toList());
  }

  public List<Object> getExecutionKeys() {
    return executionMap.keySet().stream().collect(Collectors.toList());
  }

  public boolean checkCompletableFuture(Object key) {
    return executionMap.containsKey(key);
  }

  public CompletableFuture<Object> findCompletableFuture(Object key) {
    return executionMap.get(key);
  }

  public LocalDateTime getExecutionTime(Object key) {
    return executionTime.get(key);
  }

  public static class AsyncRunnable implements Runnable {

    private final Runnable runnable;
    private final Object asyncKey;
    private final Consumer<LocalDateTime> setLastExecutionStart;
    private final Consumer<LocalDateTime> setLastExecutionStop;
    private final CompletableFuture<Object> cfuture;
    private final Map<Object, CompletableFuture<Object>> executionMap;
    private final Map<Object, LocalDateTime> executionTime;

    public AsyncRunnable(Runnable runnable,
                         Object asyncKey,
                         Consumer<LocalDateTime> setLastExecutionStart,
                         Consumer<LocalDateTime> setLastExecutionStop,
                         CompletableFuture<Object> cfuture,
                         Map<Object, CompletableFuture<Object>> executionMap,
                         Map<Object, LocalDateTime> executionTime) {
      this.runnable = runnable;
      this.asyncKey = asyncKey;
      this.setLastExecutionStart = setLastExecutionStart;
      this.setLastExecutionStop = setLastExecutionStop;
      this.cfuture = cfuture;
      this.executionMap = executionMap;
      this.executionTime = executionTime;
    }

    @Override
    public void run() {
      threadLocalCompletableFuture.set(cfuture);
      setLastExecutionStart.accept(LocalDateTime.now());
      executionTime.put(asyncKey, LocalDateTime.now());

      try {
        runnable.run();
      }
      catch (CancellationException ex) {
        cfuture.cancel(true);
        log.error("Async method execution cancelled with an exception: {}", ex);
      }
      catch (Exception ex) {
        cfuture.completeExceptionally(ex);
        log.error("Async method execution completed with an exception: {}", ex);
      }
      finally {
        executionMap.remove(asyncKey);
        executionTime.remove(asyncKey);
        setLastExecutionStop.accept(LocalDateTime.now());
      }

    }
  }
}
