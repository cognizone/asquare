package zone.cogni.asquare.cube.monitoredpool;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A thread pool which also monitors its invocations:
 * <ul>
 *   <li> other invocations are rejected while current one is processing </li>
 *   <li> gives a summary of total time it took to process the invocation </li>
 *   <li> gives a summary of successes and failures </li>
 *   <li> gives a detailed list of errors </li>
 * </ul>
 */
public class MonitoredPool {

  private static final Logger log = LoggerFactory.getLogger(MonitoredPool.class);

  private final String name;
  private final int threadPoolSize;
  private ThreadPoolExecutor threadPool;

  public MonitoredPool(String name, int threadPoolSize) {
    this.name = name;
    this.threadPoolSize = threadPoolSize;
    this.threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadPoolSize);

    startMonitoring();
  }

  private void startMonitoring() {
    Thread monitor = new Thread(this::getMonitoringRunnable, name + "-Monitor");
    monitor.setDaemon(true);
    monitor.start();
  }

  private void getMonitoringRunnable() {
    boolean previousStateActive = threadPool.getActiveCount() != 0;
    log.info("[pool '{}' activity] {}active", name, (previousStateActive ? "" : "NOT "));
    while (true) {
      try {
        Thread.sleep(1000);
        boolean currentStateActive = threadPool.getActiveCount() != 0;
        if (previousStateActive != currentStateActive) {
          log.info("[pool '{}' activity] {}active", name, (currentStateActive ? "" : "NOT "));
          previousStateActive = currentStateActive;
        }
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @PreDestroy
  public void destroy() {
    threadPool.shutdown();
  }

  public boolean isActive() {
    return threadPool.getActiveCount() > 0;
  }

  public <V> Collection<V> invoke(Collection<Callable<V>> callables, List<Consumer<InvocationContext<V>>> afterCodeBlocks) {
    if (isActive()) throw new RuntimeException("cannot be invoked, another process is running");


    InvocationContext<V> context = new InvocationContext(name);

    List<Future<CallableResult<V>>> futures = submit(callables);
    List<CallableResult<V>> results = getCallableResults(futures);

    context.setResults(results);

    if (results == null) {
      log.info("Failures happened. Cannot log a report.");
      return null;
    }

    afterCodeBlocks.forEach(block -> block.accept(context));

    return getActualResults(results);
  }

  public static class InvocationContext<V> {
    String poolName;
    LocalDateTime start;
    LocalDateTime end;
    List<CallableResult<V>> results;

    public InvocationContext(String poolName) {
      this.poolName = poolName;
      this.start = LocalDateTime.now();
    }

    public void setResults(List<CallableResult<V>> results) {
      this.results = results;
      this.end = LocalDateTime.now();
    }

    public String getPoolName() {
      return poolName;
    }

    public LocalDateTime getStart() {
      return start;
    }

    public LocalDateTime getEnd() {
      return end;
    }

    public List<CallableResult<V>> getResults() {
      return results;
    }

    public String getTotalTime() {
      long hours = start.until(end, ChronoUnit.HOURS);
      long minutes = start.until(end, ChronoUnit.MINUTES);
      long seconds = start.until(end, ChronoUnit.SECONDS);

      String hoursString = hours == 0 ? "" : "" + hours + "h ";
      String minutesString = (hours == 0 && minutes == 0) ? "" : "" + (minutes - hours * 60) + "m ";
      String secondsString = "" + (seconds - (minutes * 60) - (hours * 3600)) + "s";
      return hoursString + minutesString + secondsString;
    }
  }

  public <V> Collection<V> invoke(Collection<Callable<V>> callables) {
    return invoke(callables, Arrays.asList(logTiming(),
                                           logSummary(),
                                           logDetailedErrors()));
  }

  private <V> List<Future<CallableResult<V>>> submit(Collection<Callable<V>> callables) {
    return callables.stream()
                    .map(this::wrapIntoCallableResult)
                    .map(threadPool::submit)
                    .collect(Collectors.toList());
  }

  private <V> Callable<CallableResult<V>> wrapIntoCallableResult(Callable<V> callable) {
    return () -> {
      try {
        V result = callable.call(); // <- what if call is hanging ???
        return new CallableResult<>(callable, result);
      }
      catch (RuntimeException e) {
        return new CallableResult<>(callable, e);
      }
    };
  }

  private <V> List<CallableResult<V>> getCallableResults(List<Future<CallableResult<V>>> futures) {
    try {
      List<CallableResult<V>> results = new ArrayList<>();
      for (Future<CallableResult<V>> future : futures) {
        results.add(future.get());
      }
      return results;
    }
    catch (InterruptedException e) {
      threadPool.shutdown();
      threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadPoolSize);
    }
    catch (ExecutionException e) {
      log.error("Thread pool processing failed.", e);
    }
    return null;
  }

  public static <V> Consumer<InvocationContext<V>> logTiming() {
    return context -> {
      log.info("[pool '{}' activity] started at {}", context.getPoolName(), context.getStart());
      log.info("[pool '{}' activity]   ended at {}", context.getPoolName(), context.getEnd());
      log.info("[pool '{}' activity] total time {}", context.getPoolName(), context.getTotalTime());
    };
  }

  public static <V> Consumer<InvocationContext<V>> logSummary() {
    return context -> {
      List<CallableResult<V>> results = context.getResults();
      log.info("================================");
      log.info(StringUtils.center("pool '" + context.getPoolName() + "' summary", 32));
      log.info("================================");
      log.info("");
      log.info("          total: " + results.size());
      log.info("        success: " + results.stream().filter(r -> r.isSuccess()).count());
      log.info("         failed: " + results.stream().filter(r -> !r.isSuccess()).count());
      log.info("");
      log.info("");
    };
  }

  public static <V> Consumer<InvocationContext<V>> logDetailedErrors() {
    return context -> {
      List<CallableResult<V>> results = context.getResults();
      // group per message and count
      Map<String, Long> problems = results.stream()
                                          .filter(r -> !r.isSuccess())
                                          .collect(Collectors.groupingBy(cr -> StringUtils.defaultIfBlank(cr.getExceptionMessage(), "exception with no message"),
                                                                         Collectors.counting()));

      // group by count, to be able to sort by count
      Map<Long, List<String>> swappedAndGrouped
        = problems.entrySet()
                  .stream()
                  .collect(Collectors.groupingBy(Map.Entry::getValue,
                                                 Collectors.mapping(Map.Entry::getKey, Collectors.toList())));

      // log count and message
      swappedAndGrouped.forEach((count, messages) -> {
        messages.forEach(message -> {
          log.info("{}: {}", StringUtils.leftPad("" + count, 6), message);
        });
      });
    };
  }

  private <V> List<V> getActualResults(List<CallableResult<V>> results) {
    return results.stream()
                  .map(CallableResult::getResult)
                  .collect(Collectors.toList());
  }
}
