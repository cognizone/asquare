package zone.cogni.asquare.triplestore.pool;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.pool2.KeyedObjectPool;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.CustomScopeConfigurer;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.SimpleThreadScope;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.context.annotation.RequestScope;
import zone.cogni.asquare.triplestore.jenamemory.LocalTdbRdfStoreService;
import zone.cogni.asquare.triplestore.pool.jenamemory.PoolableLocalTdbRdfStoreService;
import zone.cogni.asquare.triplestore.pool.key.LocalTdbPoolKey;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles("test")
class LocalTdbRdfStoreServicePoolTest {

  private static KeyedObjectPool<LocalTdbPoolKey, PoolableLocalTdbRdfStoreService> pool;

  @TempDir
  static Path tmpFolder;
  private static  Path databasesPath;

  @Autowired
  private ApplicationContext applicationContext;

  @BeforeAll
  static void beforeAll() throws IOException {
    databasesPath = tmpFolder.resolve("databases");
    Files.createDirectory(databasesPath);
    pool = LocalTdbRdfStoreServicePool.getInstance().getPool();
  }

  @AfterAll
  static void afterAll() throws IOException {
    LocalTdbRdfStoreServicePool.getInstance().close();
    try (final Stream<Path> walk = Files.walk(databasesPath)) {
      walk
        .sorted(Comparator.reverseOrder())
        .forEach(path -> {
          try {
            Files.delete(path);
          }
          catch (final IOException e) {
            throw new RuntimeException(e);
          }
        });
    }
  }

  @BeforeEach
  void setUp() throws Exception {
    LocalTdbRdfStoreServicePool.setRemoveAbandonedTimeout(Duration.ofNanos(1L));
    LocalTdbRdfStoreServicePool.setTimeBetweenEvictionRuns(Duration.ofHours(-1));
    LocalTdbRdfStoreServicePool.getInstance().clear();
  }

  @Test
  void testOneStore() throws Exception {
    assertTrue(
      PoolUtil.safeCall(pool, new LocalTdbPoolKey(
        databasesPath, "http://example.com/test"),
        (Function<PoolableLocalTdbRdfStoreService, Boolean>) store -> {
          assertEquals(0, store.size());
          store.executeUpdateQuery(
            "INSERT DATA { <http://test.com/subject> <http://test.com/predicate>  \"test\" . }"
          );
          assertEquals(1, store.size());

          return true;
        }
      )
    );
  }

  @Test
  void testSameStoreIsReturnedOnSameThread() throws Exception {
    final ExecutorService executor = Executors.newFixedThreadPool(2);
    final LocalTdbPoolKey key = new LocalTdbPoolKey(databasesPath, "http://example.com/test");

    final CountDownLatch waitForStart = new CountDownLatch(1);
    final Future<LocalTdbRdfStoreService> store1 = executor.submit(() -> {
      // make sure we use two different new instances of the key but still we get the same store
      final Optional<PoolableLocalTdbRdfStoreService> s1 = getProvider().getStore(new LocalTdbPoolKey(databasesPath, "http://example.com/test"));
      final Optional<PoolableLocalTdbRdfStoreService> s2 = getProvider().getStore(key);
      assertSame(s1.get(), s2.get());
      waitForStart.countDown();
      return s2.get();
    });

    // same key instance but different store
    final Future<LocalTdbRdfStoreService> store2 = executor.submit(() -> {
      waitForStart.await();
      return getProvider().getStore(key).get();
    });
    assertNotSame(store1.get(), store2.get());  // same key but different thread
  }

  @Test
  void testEvictionRuns(final CapturedOutput output) throws Exception {
    // waiting for all cleanup from evictor to be finished
    for (int i = 0; i < 3; i++) {
      Thread.sleep(1000L);
    }
    final int length = output.getAll().length();

    LocalTdbRdfStoreServicePool.setTimeBetweenEvictionRuns(Duration.ofMillis(1000));
    LocalTdbRdfStoreServicePool.setRemoveAbandonedTimeout(Duration.ofMillis(1000));
    final ExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    executor.submit(() -> {
      final Optional<PoolableLocalTdbRdfStoreService> store = getProvider().getStore(
        new LocalTdbPoolKey(databasesPath, "http://example.com/test")
      );
      store.get().executeUpdateQuery(
        "INSERT DATA { <http://test.com/subject> <http://test.com/predicate> 1 . }"
      );
      store.get().constructAllTriples();
      store.get().close();
    });

    // wait for the logs to arrive, sleep to give chance for 'commons-pool-evictor' thread
    for (int i = 0; i < 3; i++) {
      Thread.sleep(1000L);
    }
    assertEquals(1, StringUtils.countMatches(output.getAll().substring(length, output.length()),
      "[commons-pool-evictor] INFO zone.cogni.asquare.triplestore.pool.factory.BaseRdfStoreServiceFactory - " +
        "Destroy object of http://example.com/test: zone.cogni.asquare.triplestore.pool.jenamemory.PoolableLocalTdbRdfStoreService@"
    ));
  }

  @Test
  void testTooManyRequestsToSameTDB(final CapturedOutput output) throws InterruptedException {
    final LocalTdbPoolKey key = new LocalTdbPoolKey(databasesPath, "http://example.com/test");

    final ExecutorService executor = Executors.newFixedThreadPool(16);
    final CountDownLatch waitForStart = new CountDownLatch(1);

    for (int i = 0; i < 16; i++) {
      executor.submit(() -> {

          final LocalTdbRdfStoreService storeService = getProvider().getStore(key).get();
          waitForStart.await();
          // let's keep store reference longer
          TimeUnit.MILLISECONDS.wait(2500); //It times out at 2s

          return storeService.constructAllTriples();
      });
    }

    waitForStart.countDown(); // release all at once
    executor.awaitTermination(4000L, TimeUnit.MILLISECONDS);
    // wait for the logs to arrive, sleep to give chance for 'commons-pool-evictor' thread
    for (int i = 0; i < 3; i++) {
      Thread.sleep(1000L);
    }
    String allOutput = output.getAll();
    System.out.println("allOutput = '" + allOutput + "'") ;
    Assertions.assertThat(StringUtils.countMatches(allOutput, "Timeout waiting for idle object"))
                      .isEqualTo(1);
//    assertEquals(1, );
  }

  private RdfStoreServiceProvider<LocalTdbPoolKey, PoolableLocalTdbRdfStoreService> getProvider() {
    return (RdfStoreServiceProvider<LocalTdbPoolKey, PoolableLocalTdbRdfStoreService>)
      applicationContext.getBean("rdfStoreServiceProvider");
  }

  @Configuration
  public static class Config {

    @Bean
    public CustomScopeConfigurer customScopeConfigurer() {
      final CustomScopeConfigurer customScopeConfigurer = new CustomScopeConfigurer();
      customScopeConfigurer.addScope("request", new SimpleThreadScope());
      return customScopeConfigurer;
    }

    @Bean
    @RequestScope
    RdfStoreServiceProvider<LocalTdbPoolKey, PoolableLocalTdbRdfStoreService> rdfStoreServiceProvider() {
      return new RdfStoreServiceProvider<>(LocalTdbRdfStoreServicePool.getInstance().getPool());
    }
  }
}
