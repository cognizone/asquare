package zone.cogni.asquare.cube.monitoredpool;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class MonitoredPoolTest {

  private static final Logger log = LoggerFactory.getLogger(MonitoredPoolTest.class);

  @Test
  public void success_test() {
    // given
    MonitoredPool monitoredPool = new MonitoredPool("success", 4);

    List<Callable<String>> callables =
            IntStream.range(1, 10)
                     .mapToObj(count -> (Callable<String>) () -> {
                       log.info("callable {}", count);
                       Thread.sleep(1000);
                       return "call-" + count;
                     })
                     .collect(Collectors.toList());

    // when
    Collection<String> results = monitoredPool.invoke(callables);
    // sleep so monitor can finish (!)
    try {
      Thread.sleep(1500);
    }
    catch (InterruptedException ignore) {
    }

    // then
    Assertions.assertThat(results).hasSize(9);
    Assertions.assertThat(results).contains("call-3", "call-9", "call-1");
    Assertions.assertThat(results).doesNotContain("call-0", "call-10", "nonsense");

    // also check pool monitor:
    //
    // [pool 'success' activity] indexing is NOT active
    // [pool 'success' activity] indexing is active
    // [pool 'success' activity] started at 2021-04-12T20:42:32.300120
    // [pool 'success' activity]   ended at 2021-04-12T20:42:35.304828
    // [pool 'success' activity] total time 3s
    // [pool 'success' activity] indexing is NOT active
    //
    // and summary:
    //
    // ================================
    //     pool + 'success' summary
    // ================================
    //
    //            total: 9
    //          success: 9
    //           failed: 0
  }

  @Test
  public void fail_test() {
    // given
    MonitoredPool monitoredPool = new MonitoredPool("fail", 4);

    List<Callable<String>> callables =
            IntStream.range(1, 10)
                     .mapToObj(count -> (Callable<String>) () -> {
                       log.info("callable {}", count);
                       boolean test = true;
                       if (test) throw new RuntimeException("error-" + count);
                       return "call-" + count;
                     })
                     .collect(Collectors.toList());

    // when
    Collection<String> results = monitoredPool.invoke(callables);

    // then
    Assertions.assertThat(results).hasSize(9);
    Assertions.assertThat(results).allMatch(Objects::isNull);
    Assertions.assertThat(results).doesNotContain("fail-3", "nonsense", "call-3");
  }

  @Test
  public void mix_test() {
    // given
    MonitoredPool monitoredPool = new MonitoredPool("mix", 4);

    List<Callable<String>> callables =
            IntStream.range(1, 10)
                     .mapToObj(count -> (Callable<String>) () -> {
                       log.info("callable {}", count);
                       if (count % 2 == 0) return "call-" + count;

                       throw new RuntimeException("error-" + count);
                     })
                     .collect(Collectors.toList());

    // when
    Collection<String> results = monitoredPool.invoke(callables);

    // then
    Assertions.assertThat(results).hasSize(9);
    Assertions.assertThat(results).contains("call-2", "call-8");
    Assertions.assertThat(results).doesNotContain("call-0", "nonsense", "call-10");

    long nullCount = results.stream().filter(Objects::isNull).count();
    Assertions.assertThat(nullCount).isEqualTo(5);
  }

}