package zone.cogni.asquare.triplestore.pool.jenamemory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.context.ActiveProfiles;
import zone.cogni.asquare.triplestore.pool.key.LocalTdbPoolKey;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("test")
class PoolableLocalTdbRdfStoreServiceTest {

  @TempDir
  Path tempDir;

  private File tdbLocation;
  private LocalTdbPoolKey poolKey;

  @BeforeEach
  void setUp() throws Exception {
    Path databasesPath = tempDir.resolve("databases");
    Files.createDirectories(databasesPath);

    poolKey = new LocalTdbPoolKey(databasesPath, "http://example.com/test");
    tdbLocation = poolKey.getDirPath().toFile();
  }

  @Test
  void testCreateFromPoolKey() {
    try (PoolableLocalTdbRdfStoreService service = PoolableLocalTdbRdfStoreService.createFrom(poolKey)) {
      assertNotNull(service);
      assertEquals(tdbLocation.getPath(), service.getTdbLocation());
    }
  }

  @Test
  void testConstructorWithTdbLocationOnly() {
    try (PoolableLocalTdbRdfStoreService service = new PoolableLocalTdbRdfStoreService(tdbLocation)) {
      assertNotNull(service);
      assertEquals(tdbLocation.getPath(), service.getTdbLocation());
    }
  }

  @Test
  void testConstructorWithTimeouts() {
    try (PoolableLocalTdbRdfStoreService service = new PoolableLocalTdbRdfStoreService(
      tdbLocation, 30, TimeUnit.SECONDS
    )) {
      assertNotNull(service);
      assertEquals(tdbLocation.getPath(), service.getTdbLocation());
    }
  }

  @Test
  void testConstructorWithFullTimeouts() {
    try (PoolableLocalTdbRdfStoreService service = new PoolableLocalTdbRdfStoreService(
      tdbLocation, 30, TimeUnit.SECONDS, 60, TimeUnit.SECONDS
    )) {
      assertNotNull(service);
      assertEquals(tdbLocation.getPath(), service.getTdbLocation());
    }
  }

  @Test
  void testConstructorWithInitFolder() {
    File initFolder = tempDir.resolve("init").toFile();
    try (PoolableLocalTdbRdfStoreService service = new PoolableLocalTdbRdfStoreService(
      tdbLocation, initFolder
    )) {
      assertNotNull(service);
      assertEquals(tdbLocation.getPath(), service.getTdbLocation());
    }
  }

  @Test
  void testConstructorWithInitFolderAndTimeouts() {
    File initFolder = tempDir.resolve("init").toFile();
    try (PoolableLocalTdbRdfStoreService service = new PoolableLocalTdbRdfStoreService(
      tdbLocation, initFolder, 30, TimeUnit.SECONDS
    )) {
      assertNotNull(service);
      assertEquals(tdbLocation.getPath(), service.getTdbLocation());
    }
  }

  @Test
  void testConstructorWithAllParameters() {
    File initFolder = tempDir.resolve("init").toFile();
    try (PoolableLocalTdbRdfStoreService service = new PoolableLocalTdbRdfStoreService(
      tdbLocation, initFolder, 30, TimeUnit.SECONDS, 60, TimeUnit.SECONDS
    )) {
      assertNotNull(service);
      assertEquals(tdbLocation.getPath(), service.getTdbLocation());
    }
  }

  @Test
  void testActivateObjectLifecycle() {
    try(PoolableLocalTdbRdfStoreService service = new PoolableLocalTdbRdfStoreService(tdbLocation)) {
      // Initially service should be ready after construction
      assertTrue(getReadyState(service));
      assertFalse(service.getDataset().isInTransaction());

      // Activate the object
      assertDoesNotThrow(service::activateObject);

      // After activation, service should still be ready and now in transaction
      assertTrue(getReadyState(service));
      assertTrue(service.getDataset().isInTransaction());
    }
  }

  @Test
  void testPassivateObjectLifecycle() throws Exception {
    try(PoolableLocalTdbRdfStoreService service = new PoolableLocalTdbRdfStoreService(tdbLocation)) {
      // First activate
      service.activateObject();
      assertTrue(service.getDataset().isInTransaction());

      // Then passivate
      assertDoesNotThrow(service::passivateObject);

      // After passivation, transaction should be ended
      assertFalse(service.getDataset().isInTransaction());
    }
  }

  @Test
  void testValidateObjectWithValidStore() throws Exception {
    try (PoolableLocalTdbRdfStoreService service = new PoolableLocalTdbRdfStoreService(tdbLocation)) {
      service.activateObject();
      assertTrue(service.validateObject(poolKey));
    }
  }

  @Test
  void testValidateObjectWithInactiveStore() {
    try (PoolableLocalTdbRdfStoreService service = new PoolableLocalTdbRdfStoreService(tdbLocation)) {
      // Newly created services should be valid even without explicit activation
      assertTrue(service.validateObject(poolKey));
    }
  }

  @Test
  void testCompleteLifecycle() throws Exception {
    try(PoolableLocalTdbRdfStoreService service = new PoolableLocalTdbRdfStoreService(tdbLocation)) {
      // Initial state - should be ready after construction
      assertTrue(getReadyState(service));
      assertFalse(service.getDataset().isInTransaction());

      // Activate
      service.activateObject();
      assertTrue(getReadyState(service));
      assertTrue(service.getDataset().isInTransaction());
      assertTrue(service.validateObject(poolKey));

      // Use the service (add some data)
      service.executeUpdateQuery(
        "INSERT DATA { <http://test.com/subject> <http://test.com/predicate> \"test value\" . }"
      );
      assertEquals(1L, service.size());

      // Passivate
      service.passivateObject();
      assertFalse(service.getDataset().isInTransaction());

      // Reactivate
      service.activateObject();
      assertTrue(getReadyState(service));
      assertTrue(service.getDataset().isInTransaction());

      // Data should still be there after reactivation
      assertEquals(1L, service.size());
    }
  }

  @Test
  void testDestroyObject() throws Exception {
    try(PoolableLocalTdbRdfStoreService service = new PoolableLocalTdbRdfStoreService(tdbLocation)) {
      service.activateObject();

      // Add some data
      service.executeUpdateQuery(
        "INSERT DATA { <http://test.com/subject> <http://test.com/predicate> \"test value\" . }"
      );
      assertEquals(1L, service.size());

      // Destroy should clean up resources - this is what we're testing
      assertDoesNotThrow(service::destroyObject);

      // After destruction, the service should be closed (ready flag should be false)
      assertFalse(getReadyState(service));
    }
  }

  @Test
  void testActivateObjectWithExistingTransaction() throws Exception {
    try(PoolableLocalTdbRdfStoreService service = new PoolableLocalTdbRdfStoreService(tdbLocation)) {
      // Manually start a transaction using the proper TDB1 approach
      service.getDataset().begin(org.apache.jena.query.TxnType.READ_COMMITTED_PROMOTE);
      assertTrue(service.getDataset().isInTransaction());

      // Activate should handle existing transaction
      assertDoesNotThrow(service::activateObject);

      assertTrue(getReadyState(service));
      assertTrue(service.getDataset().isInTransaction());
    }
  }

  @Test
  void testPassivateObjectWithoutTransaction() {
    try (PoolableLocalTdbRdfStoreService service = new PoolableLocalTdbRdfStoreService(tdbLocation)) {
      // Passivate without any transaction should not throw
      assertDoesNotThrow(service::passivateObject);
    }
  }

  @Test
  void testMultipleActivatePassivateCycles() throws Exception {
    try(PoolableLocalTdbRdfStoreService service = new PoolableLocalTdbRdfStoreService(tdbLocation)) {
      for (int i = 0; i < 3; i++) {
        // Activate
        service.activateObject();
        assertTrue(getReadyState(service));
        assertTrue(service.getDataset().isInTransaction());

        // Add some data
        service.executeUpdateQuery(
          "INSERT DATA { <http://test.com/subject" + i + "> <http://test.com/predicate> \"value" + i + "\" . }"
        );

        // Passivate
        service.passivateObject();
        assertFalse(service.getDataset().isInTransaction());
      }

      // Final check - all data should be persisted
      service.activateObject();
      assertEquals(3L, service.size());
    }
  }

  private boolean getReadyState(PoolableLocalTdbRdfStoreService service) {
    try {
      Field readyField = service.getClass().getSuperclass().getDeclaredField("ready");
      readyField.setAccessible(true);
      AtomicBoolean ready = (AtomicBoolean) readyField.get(service);
      return ready.get();
    } catch (Exception e) {
      throw new RuntimeException("Failed to access ready field", e);
    }
  }
}