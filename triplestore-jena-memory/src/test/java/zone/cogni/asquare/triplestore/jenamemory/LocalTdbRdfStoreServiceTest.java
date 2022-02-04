package zone.cogni.asquare.triplestore.jenamemory;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.sem.jena.JenaUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
class LocalTdbRdfStoreServiceTest {

  private static final Logger log = LoggerFactory.getLogger(LocalTdbRdfStoreServiceTest.class);

  @TempDir
  Path tmpFolder;

  @Value("classpath:init_ctms_tdb")
  Resource defaultTdbContent;

  private Path ctmsTdbPath;
  private File ctmsTdbFileSpy;
  private LocalTdbRdfStoreService store;

  @BeforeEach
  void setUp() throws IOException {
    ctmsTdbPath = tmpFolder.resolve("ctms_sk_nsc_20221901");
    Files.createDirectory(ctmsTdbPath);

    ctmsTdbFileSpy = Mockito.spy(ctmsTdbPath.toFile());
    store = new LocalTdbRdfStoreService(ctmsTdbFileSpy, defaultTdbContent.getFile());
    validateTurtleSerialization();
  }

  @AfterEach
  void tearDown() throws IOException {
    store.close();
    try (final Stream<Path> walk = Files.walk(ctmsTdbPath)) {
      walk
        .sorted(Comparator.reverseOrder())
        .forEach(path -> {
          try {
            Files.delete(path);
          }
          catch (final IOException e) {
            log.error("", e);
          }
        });
    }
  }

  @Test
  void testParallelInit() throws InterruptedException, ExecutionException, TimeoutException {
    closeRdfStoreServiceWithWait();

    final int nbrOfTries = 100;
    final List<Future<LocalTdbRdfStoreService>> futureStores = new ArrayList<>(nbrOfTries);

    final ExecutorService executor = Executors.newFixedThreadPool(nbrOfTries);
    for (int i = 0; i < nbrOfTries; i++) {
      futureStores.add(executor.submit(() -> new LocalTdbRdfStoreService(ctmsTdbPath.toFile(), null)));
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

    closeRdfStoreServiceWithWait();
  }

  @Test
  void testParallelModelModificationWithDifferentTDBs() throws InterruptedException, ExecutionException {
    final long startingSize = store.getModel().size();

    final int nbrOfTries = 100;

    final List<LocalTdbRdfStoreService> stores = IntStream.range(0, nbrOfTries)
      .mapToObj(i -> new LocalTdbRdfStoreService(ctmsTdbPath.toFile(), null))
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
      startingSize + expectedValues.size(), stores.get(0).getModel().size(),
      () -> {
          expectedValues.removeAll(
            store.getModel().listObjectsOfProperty(
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

    closeRdfStoreServiceWithWait();
  }

  @Test
  void testParallelModelModificationWithSharedTDB() throws InterruptedException, ExecutionException {
    final long startingSize = store.getModel().size();

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
      startingSize + expectedValues.size(), store.getModel().size(),
      () -> {
        expectedValues.removeAll(
          store.getModel().listObjectsOfProperty(
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

    closeRdfStoreServiceWithWait();
  }

  @Test
  void testParallelModelModificationAndTdbCloseWithDifferentTDBs() throws InterruptedException, ExecutionException {
    final long startingSize = store.getModel().size();

    final int nbrOfTries = 100;

    final List<LocalTdbRdfStoreService> stores = IntStream.range(0, nbrOfTries)
      .mapToObj(i -> new LocalTdbRdfStoreService(ctmsTdbPath.toFile(), null))
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

      if(i == (nbrOfTries / 2)) {
        executor.submit(store::close);
      }
    }

    shutDown(executor);

    validateTurtleSerialization(new LocalTdbRdfStoreService(ctmsTdbPath.toFile(), null));
    assertEquals(startingSize + (2 * nbrOfTries), store.getModel().size());

    closeRdfStoreServiceWithWait();
  }

  // reproduce bug with the message: java.util.ConcurrentModificationException: Reader = 1, Writer = 1
  // or bug with: java.util.ConcurrentModificationException: Iterator: started at 1575, now 1576
  @Test
  void reproduceConcurrentModificationException() throws InterruptedException, ExecutionException {
    final int nbrOfTries = 100;

    final ExecutorService executor = Executors.newFixedThreadPool(2 * nbrOfTries);
    for (int i = 0; i < nbrOfTries; i++) {
      final int unique = i + 1;

      executor.submit(() -> store.executeUpdateQuery(
        "INSERT DATA { <http://test.com/subject> <http://test.com/predicate> " + unique + " . }"
      ));
      executor.submit(() -> store.executeUpdateQuery(
        "INSERT DATA { <http://test.com/subject> <http://test.com/predicate> " + (-unique) + " . }"
      ));
    }
    executor.shutdownNow();
    closeRdfStoreServiceWithWait();
    validateTurtleSerialization(new LocalTdbRdfStoreService(ctmsTdbPath.toFile(), null));
  }

  @Test
  void interruptLongRunningWrites() throws InterruptedException, ExecutionException {
    final ExecutorService executor = Executors.newFixedThreadPool(2);

    final Consumer<Integer> longWriteOperation = param -> {
      int i = param;
      while(true) {
        final int unique = i++;
        store.executeUpdateQuery(
          "INSERT DATA { <http://test.com/subject> <http://test.com/predicate> " + unique  + " . }"
        );
      }
    };

    executor.submit(() -> longWriteOperation.accept(1));
    executor.submit(() -> longWriteOperation.accept(20000));
    executor.awaitTermination(3L, TimeUnit.SECONDS);
    executor.shutdownNow();

    validateTurtleSerialization();
    closeRdfStoreServiceWithWait();
  }

  @Test
  void interruptLongRunningWritesOnDifferentTDBs() throws InterruptedException, ExecutionException {
    final ExecutorService executor = Executors.newFixedThreadPool(2);

    final LocalTdbRdfStoreService store1 = new LocalTdbRdfStoreService(ctmsTdbPath.toFile(), null);
    final LocalTdbRdfStoreService store2 = new LocalTdbRdfStoreService(ctmsTdbPath.toFile(), null);

    final BiConsumer<LocalTdbRdfStoreService, Integer> longWriteOperation = (s, param) -> {
      int i = param;
      while(true) {
        final int unique = i++;
        s.executeUpdateQuery(
          "INSERT DATA { <http://test.com/subject> <http://test.com/predicate> " + unique  + " . }"
        );
      }
    };

    executor.submit(() -> longWriteOperation.accept(store1, 1));
    executor.submit(() -> longWriteOperation.accept(store2, 20000));
    executor.awaitTermination(3L, TimeUnit.SECONDS);
    executor.shutdownNow();

    assertEquals(store.getModel().size(), store2.getModel().size());
    assertEquals(store1.getModel().size(), store2.getModel().size());

    validateTurtleSerialization(store1);
    validateTurtleSerialization(store2);

    closeRdfStoreServiceWithWait();
  }

  @Test
  void interruptLongUpdateQuery() throws InterruptedException, ExecutionException {
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

    executor.awaitTermination(10L, TimeUnit.MILLISECONDS);
    executor.shutdownNow();

    validateTurtleSerialization();
    closeRdfStoreServiceWithWait();
  }

  @Test
  void interruptLongUpdateQueryOnDifferentTDBs() throws InterruptedException, ExecutionException {
    final int nbrOfTries = 100;

    final List<LocalTdbRdfStoreService> stores = IntStream.range(0, nbrOfTries)
      .mapToObj(i -> new LocalTdbRdfStoreService(ctmsTdbPath.toFile(), null))
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

    executor.awaitTermination(10L, TimeUnit.MILLISECONDS);
    executor.shutdownNow();

    stores.forEach(this::validateTurtleSerialization);
    assertDifferentSizes(stores);
  }

  @Test
  void testParallelClose() throws InterruptedException {
    final int nbrOfTries = 100;

    final ExecutorService executor = Executors.newFixedThreadPool(nbrOfTries);
    Mockito.clearInvocations(ctmsTdbFileSpy);
    IntStream.range(0, nbrOfTries).mapToObj(i -> store).forEach(s -> executor.submit(s::close));
    shutDown(executor);

    Mockito.verify(ctmsTdbFileSpy, Mockito.times(2)).getPath();
  }

  @Test
  void testAddDataWithGraphUri() {
    Assertions.assertThrows(RuntimeException.class, () -> {
      store.addData(ModelFactory.createDefaultModel(), "test");
    }, "Add data with graph not supported");

  }

  private void closeRdfStoreServiceWithWait() throws ExecutionException, InterruptedException {
    closeRdfStoreServiceWithWait(store);
  }

  private void closeRdfStoreServiceWithWait(final RdfStoreService store) throws ExecutionException, InterruptedException {
    final ExecutorService closeExecutor = Executors.newSingleThreadExecutor();
    final Future<?> waitForClose = closeExecutor.submit(store::close);
    try {
      waitForClose.get(5L, TimeUnit.SECONDS);
    } catch (final TimeoutException e) {
      waitForClose.cancel(true);
      throw new RuntimeException("Couldn't close TDB of: " + store.toString());
    } finally {
      closeExecutor.shutdownNow();
    }
  }

  private void validateTurtleSerialization() {
    validateTurtleSerialization(store);
  }

  private void validateTurtleSerialization(final RdfStoreService store) {
    JenaUtils.toByteArray(store.executeConstructQuery(
      "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o. }"
    ), "TTL");
  }

  private void shutDown(final ExecutorService executor) throws InterruptedException {
    executor.shutdown();
    if(!executor.awaitTermination(10L, TimeUnit.SECONDS)) {
      log.info(" ... still running threads: {}", executor.shutdownNow());
      fail();
    }
  }

  private void assertDifferentSizes(final List<LocalTdbRdfStoreService> stores) {
    final Set<Long> differentSizes = stores.stream()
      .map(LocalTdbRdfStoreService::getModel)
      .map(Model::size)
      .collect(Collectors.toSet());

    assertEquals(1, differentSizes.size());
  }
}
