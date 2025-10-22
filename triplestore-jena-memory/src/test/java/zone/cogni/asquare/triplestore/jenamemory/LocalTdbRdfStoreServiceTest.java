package zone.cogni.asquare.triplestore.jenamemory;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetRewindable;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.sparql.engine.binding.Binding;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import zone.cogni.sem.jena.JenaUtils;
import zone.cogni.sem.jena.template.MemoryAwareListResultSetHandler;
import zone.cogni.sem.jena.template.TooManyResultsException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(SpringExtension.class)
@ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles("test")
class LocalTdbRdfStoreServiceTest {

  private static final Logger log = LoggerFactory.getLogger(LocalTdbRdfStoreServiceTest.class);

  @TempDir
  Path tmpFolder;

  @Value("classpath:init_sample_tdb")
  Resource defaultTdbContent;

  private Path tdbPath;
  private LocalTdbRdfStoreService store;

  @BeforeEach
  void setUp() throws IOException {
    tdbPath = tmpFolder.resolve("sample_data_20221901");
    Files.createDirectory(tdbPath);

    store = new LocalTdbRdfStoreService(tdbPath.toFile(), defaultTdbContent.getFile());
    validateTurtleSerialization();
  }

  @AfterEach
  void tearDown() throws IOException {
    store.close();
    try (final Stream<Path> walk = Files.walk(tdbPath)) {
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

  @Test
  void testParallelInit() throws InterruptedException {
    store.close();

    final int nbrOfTries = 100;
    final List<Future<LocalTdbRdfStoreService>> futureStores = new ArrayList<>(nbrOfTries);

    final ExecutorService executor = Executors.newFixedThreadPool(nbrOfTries);
    for (int i = 0; i < nbrOfTries; i++) {
      futureStores.add(executor.submit(() -> new LocalTdbRdfStoreService(tdbPath.toFile(), null)));
    }

    shutDown(executor);

    final List<LocalTdbRdfStoreService> stores = futureStores.stream()
                                                             .map(localTdbRdfStoreServiceFuture -> {
                                                               try {
                                                                 return localTdbRdfStoreServiceFuture.get(1L, TimeUnit.SECONDS);
                                                               }
                                                               catch (final TimeoutException | InterruptedException | ExecutionException e) {
                                                                 throw new RuntimeException(e);
                                                               }
                                                             })
                                                             .collect(Collectors.toList());

    stores.forEach(this::validateTurtleSerialization);
    assertDifferentSizes(stores);

    store.close();
  }

  @Test
  void testParallelModelModificationWithDifferentTDBs() throws InterruptedException {
    final long startingSize = store.size();

    final int nbrOfTries = 100;

    final List<LocalTdbRdfStoreService> stores = IntStream.range(0, nbrOfTries)
                                                          .mapToObj(i -> new LocalTdbRdfStoreService(tdbPath.toFile(), null))
                                                          .collect(Collectors.toList());

    final Set<Integer> expectedValues = new HashSet<>(2 * nbrOfTries);
    final ExecutorService executor = Executors.newFixedThreadPool(2 * nbrOfTries);

    for (int i = 0; i < nbrOfTries; i++) {
      final LocalTdbRdfStoreService s = stores.get(i);
      final int unique = i + 1;
      expectedValues.add(unique);
      expectedValues.add(-unique);

      executor.submit(() -> s.executeUpdateQuery(
              "INSERT DATA { <http://test.com/subject> <http://test.com/predicate> " + unique + " . }"
      ));
      executor.submit(() -> s.executeUpdateQuery(
              "INSERT DATA { <http://test.com/subject> <http://test.com/predicate> " + (-unique) + " . }"
      ));
    }

    shutDown(executor);

    stores.forEach(this::validateTurtleSerialization);
    assertDifferentSizes(stores);

    assertEquals(
            startingSize + expectedValues.size(), stores.get(0).size(),
            () -> {
              expectedValues.removeAll(
                      store.constructAllTriples().listObjectsOfProperty(
                                   ResourceFactory.createResource("http://test.com/subject"),
                                   ResourceFactory.createProperty("http://test.com/predicate")
                           ).toList().stream()
                           .map(RDFNode::asLiteral)
                           .map(Literal::getInt)
                           .collect(Collectors.toSet())
              );
              return "Missing triples (" + expectedValues.size() + "): " + expectedValues.stream()
                                                                                         .sorted()
                                                                                         .map(String::valueOf)
                                                                                         .collect(Collectors.joining(", "));
            }
    );

    store.close();
  }

  @Test
  void testParallelModelModificationWithSharedTDB() throws InterruptedException {
    final long startingSize = store.size();

    final int nbrOfTries = 100;

    final Set<Integer> expectedValues = new HashSet<>(2 * nbrOfTries);
    final ExecutorService executor = Executors.newFixedThreadPool(2 * nbrOfTries);

    for (int i = 0; i < nbrOfTries; i++) {
      final int unique = i + 1;
      expectedValues.add(unique);
      expectedValues.add(-unique);

      executor.submit(() -> store.executeUpdateQuery(
              "INSERT DATA { <http://test.com/subject> <http://test.com/predicate> " + unique + " . }"
      ));
      executor.submit(() -> store.executeUpdateQuery(
              "INSERT DATA { <http://test.com/subject> <http://test.com/predicate> " + (-unique) + " . }"
      ));
    }

    shutDown(executor);

    validateTurtleSerialization();

    assertEquals(
            startingSize + expectedValues.size(), store.size(),
            () -> {
              expectedValues.removeAll(
                      store.constructAllTriples().listObjectsOfProperty(
                                   ResourceFactory.createResource("http://test.com/subject"),
                                   ResourceFactory.createProperty("http://test.com/predicate")
                           ).toList().stream()
                           .map(RDFNode::asLiteral)
                           .map(Literal::getInt)
                           .collect(Collectors.toSet())
              );
              return "Missing triples (" + expectedValues.size() + "): " + expectedValues.stream()
                                                                                         .sorted()
                                                                                         .map(String::valueOf)
                                                                                         .collect(Collectors.joining(", "));
            }
    );

    store.close();
  }

  @Test
  void testParallelModelModificationAndTdbCloseWithDifferentTDBs() throws InterruptedException {
    final long startingSize = store.size();

    final int nbrOfTries = 100;

    final List<LocalTdbRdfStoreService> stores = IntStream.range(0, nbrOfTries)
                                                          .mapToObj(i -> new LocalTdbRdfStoreService(tdbPath.toFile(), null))
                                                          .collect(Collectors.toList());

    final ExecutorService executor = Executors.newFixedThreadPool((2 * nbrOfTries) + 1);
    for (int i = 0; i < nbrOfTries; i++) {
      final LocalTdbRdfStoreService s = stores.get(i);
      final int unique = i + 1;

      executor.submit(() -> s.executeUpdateQuery(
              "INSERT DATA { <http://test.com/subject> <http://test.com/predicate> " + unique + " . }"
      ));
      executor.submit(() -> s.executeUpdateQuery(
              "INSERT DATA { <http://test.com/subject> <http://test.com/predicate> " + (-unique) + " . }"
      ));

      if (i == (nbrOfTries / 2)) {
        executor.submit(store::close);
      }
    }

    shutDown(executor);

    validateTurtleSerialization(new LocalTdbRdfStoreService(tdbPath.toFile(), null));
    assertEquals(startingSize + (2 * nbrOfTries), stores.get(0).size());

    store.close();
  }

  // reproduce bug with the message: java.util.ConcurrentModificationException: Reader = 1, Writer = 1
  // or bug with: java.util.ConcurrentModificationException: Iterator: started at 1575, now 1576
  @Test
  void reproduceConcurrentModificationException() throws InterruptedException {
    final int nbrOfTries = 1;

    final CountDownLatch countDownLatch = new CountDownLatch(nbrOfTries);
    final ExecutorService executor = Executors.newFixedThreadPool(2 * nbrOfTries);
    for (int i = 0; i < nbrOfTries; i++) {
      final int unique = i + 1;

      executor.submit(() -> {
        store.executeUpdateQuery("INSERT DATA { <http://test.com/subject> <http://test.com/predicate> " + unique + " . }");
        countDownLatch.countDown();
      });
      executor.submit(() -> {
        store.executeUpdateQuery("INSERT DATA { <http://test.com/subject> <http://test.com/predicate> " + (-unique) + " . }");
        countDownLatch.countDown();
      });
    }
    countDownLatch.await();
    executor.shutdownNow();

    store.forceRelease();
    validateTurtleSerialization(new LocalTdbRdfStoreService(tdbPath.toFile(), null));
  }

  @Test
  void interruptLongRunningWrites() throws InterruptedException {
    final ExecutorService executor = Executors.newFixedThreadPool(2);

    final Consumer<Integer> longWriteOperation = param -> {
      int i = param;
      while (true) {
        final int unique = i++;
        store.executeUpdateQuery(
                "INSERT DATA { <http://test.com/subject> <http://test.com/predicate> " + unique + " . }"
        );
      }
    };

    executor.submit(() -> longWriteOperation.accept(1));
    executor.submit(() -> longWriteOperation.accept(20000));
    executor.awaitTermination(3L, TimeUnit.SECONDS);
    executor.shutdownNow();

    store.forceRelease();
    validateTurtleSerialization(new LocalTdbRdfStoreService(tdbPath.toFile(), null));
  }

  @Test
  void interruptLongRunningWritesOnDifferentTDBs() throws InterruptedException {
    final ExecutorService executor = Executors.newFixedThreadPool(2);

    final LocalTdbRdfStoreService store1 = new LocalTdbRdfStoreService(tdbPath.toFile(), null);
    final LocalTdbRdfStoreService store2 = new LocalTdbRdfStoreService(tdbPath.toFile(), null);

    final BiConsumer<LocalTdbRdfStoreService, Integer> longWriteOperation = (s, param) -> {
      int i = param;
      while (true) {
        final int unique = i++;
        s.executeUpdateQuery(
                "INSERT DATA { <http://test.com/subject> <http://test.com/predicate> " + unique + " . }"
        );
      }
    };

    executor.submit(() -> longWriteOperation.accept(store1, 1));
    executor.submit(() -> longWriteOperation.accept(store2, 20000));

    executor.shutdown();
    executor.awaitTermination(3L, TimeUnit.SECONDS);
    executor.shutdownNow();

    store.forceRelease();
    validateTurtleSerialization(new LocalTdbRdfStoreService(tdbPath.toFile(), null));
  }

  @Test
  void interruptLongUpdateQuery() {
    final int nbrOfTries = 100;

    final ExecutorService executor = Executors.newFixedThreadPool(2 * nbrOfTries);
    for (int i = 0; i < nbrOfTries; i++) {

      final StringBuilder queryBuilder1 = new StringBuilder("INSERT DATA {\n");
      final StringBuilder queryBuilder2 = new StringBuilder("INSERT DATA {\n");
      for (int j = 0; j <= nbrOfTries * 100; j += nbrOfTries) {
        final int unique = (i + 1) + j;
        queryBuilder1.append("<http://test.com/subject> <http://test.com/predicate> ").append(unique).append(" .\n");
        queryBuilder2.append("<http://test.com/subject> <http://test.com/predicate> ").append(-unique).append(" .\n");
      }

      executor.submit(() -> {
        store.executeUpdateQuery(queryBuilder1 + "}");
      });
      executor.submit(() -> {
        store.executeUpdateQuery(queryBuilder2 + "}");
      });
    }

    executor.shutdownNow();

    validateTurtleSerialization();
    store.close();
  }

  @Test
  void interruptLongUpdateQueryOnDifferentTDBs() {
    final int nbrOfTries = 100;

    final List<LocalTdbRdfStoreService> stores = IntStream.range(0, nbrOfTries)
                                                          .mapToObj(i -> new LocalTdbRdfStoreService(tdbPath.toFile(), null))
                                                          .collect(Collectors.toList());

    final ExecutorService executor = Executors.newFixedThreadPool(2 * nbrOfTries);
    for (int i = 0; i < nbrOfTries; i++) {
      final LocalTdbRdfStoreService s = stores.get(i);

      final StringBuilder queryBuilder1 = new StringBuilder("INSERT DATA {\n");
      final StringBuilder queryBuilder2 = new StringBuilder("INSERT DATA {\n");
      for (int j = 0; j <= nbrOfTries * 100; j += nbrOfTries) {
        final int unique = (i + 1) + j;
        queryBuilder1.append("<http://test.com/subject> <http://test.com/predicate> ").append(unique).append(" .\n");
        queryBuilder2.append("<http://test.com/subject> <http://test.com/predicate> ").append(-unique).append(" .\n");
      }

      executor.submit(() -> {
        s.executeUpdateQuery(queryBuilder1 + "}");
      });
      executor.submit(() -> {
        s.executeUpdateQuery(queryBuilder2 + "}");
      });
    }

    executor.shutdownNow();

    store.forceRelease();
    validateTurtleSerialization(new LocalTdbRdfStoreService(tdbPath.toFile(), null));
  }

  @Test
  void testParallelClose(final CapturedOutput output) throws InterruptedException {
    final int nbrOfTries = 100;

    final ExecutorService executor = Executors.newFixedThreadPool(nbrOfTries);
    IntStream.range(0, nbrOfTries).mapToObj(i -> store).forEach(s -> executor.submit(s::close));
    shutDown(executor);

    assertEquals(1, StringUtils.countMatches(output.getAll(), ".. .. closing TDB - "));
  }

  @Test
  void testAddDataWithGraphUri() {
    Assertions.assertThrows(RuntimeException.class, () ->
                                    store.addData(ModelFactory.createDefaultModel(), "test")
            , "Add data with graph not supported");
  }

  @Test
  void testTimeoutQuery() {
    // not sure why this keeps failing on my laptop
    final LocalTdbRdfStoreService timeoutStore = new LocalTdbRdfStoreService(
            tdbPath.toFile(), 1L, TimeUnit.NANOSECONDS, 1L, TimeUnit.NANOSECONDS
    );
    Assertions.assertThrows(RuntimeException.class, getConstructAllTriples(timeoutStore));
  }

  private Executable getConstructAllTriples(LocalTdbRdfStoreService timeoutStore) {
    return () -> {
      timeoutStore.constructAllTriples();
      try {
        Thread.sleep(100);
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    };
  }

  @Disabled
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  @Test
  void testMemorySafeResultSetHandling() throws InterruptedException {
    // load spam into memory
    final List<String> spam = IntStream.range(0, 300000)
                                       .mapToObj(i -> "text takes 80 bytes " + i) // 80 bytes + the size of i as string
                                       .collect(Collectors.toList());

    assertThrows(TooManyResultsException.class, () -> {
      final List<String> result = new MemoryAwareListResultSetHandler<String>() {
        @Override
        protected String handleRow(final QuerySolution querySolution) {
          try {
            TimeUnit.NANOSECONDS.sleep(1); // mimic the slowness of reading a DB iterator
          }
          catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
          return "text takes 80 bytes " + UUID.randomUUID();
        }
      }.handle(
              new ResultSet() {

                private int counter;

                @Override
                public boolean hasNext() {
                  return true;
                }

                @Override
                public QuerySolution next() {
                  ++counter;
                  return null;
                }

                @Override
                public void forEachRemaining(Consumer<? super QuerySolution> consumer) {

                }

                @Override
                public QuerySolution nextSolution() {
                  return null;
                }

                @Override
                public Binding nextBinding() {
                  return null;
                }

                @Override
                public int getRowNumber() {
                  return counter;
                }

                @Override
                public List<String> getResultVars() {
                  return null;
                }

                @Override
                public Model getResourceModel() {
                  return null;
                }

                @Override
                public ResultSetRewindable rewindable() {
                  return ResultSet.super.rewindable();
                }

                @Override
                public ResultSet materialise() {
                  return ResultSet.super.materialise();
                }

                @Override
                public void close() {

                }
              }
      );
    });
  }

  private void validateTurtleSerialization() {
    validateTurtleSerialization(store);
  }

  private void validateTurtleSerialization(final LocalTdbRdfStoreService store) {
    final Model model = store.constructAllTriples();
    JenaUtils.toByteArray(model, "TTL");
  }

  private void shutDown(final ExecutorService executor) throws InterruptedException {
    executor.shutdown();
    if (!executor.awaitTermination(1L, TimeUnit.MINUTES)) {
      log.info(" ... still running threads: {}", executor.shutdownNow());
      fail("Couldn't stop thread pool after specified time");
    }
  }

  private void assertDifferentSizes(final List<LocalTdbRdfStoreService> stores) {
    final Set<Long> differentSizes = stores.stream()
                                           .map(LocalTdbRdfStoreService::size)
                                           .collect(Collectors.toSet());

    assertEquals(1, differentSizes.size());
  }
}
