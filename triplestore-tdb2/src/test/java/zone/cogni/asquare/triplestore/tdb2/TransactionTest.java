package zone.cogni.asquare.triplestore.tdb2;

import io.vavr.control.Try;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import zone.cogni.asquare.transactional.ASquareTransaction;
import zone.cogni.asquare.transactional.ASquareTransactionType;
import zone.cogni.asquare.transactional.ASquareTransactional;
import zone.cogni.asquare.transactional.ASquareTransactionalAspect;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.sem.jena.JenaUtils;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {TransactionTest.Config.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TransactionTest {

  @Autowired RdfStoreService storeService;
  @Autowired TestService testService;
  @Autowired ASquareTransaction transaction;

  @BeforeEach
  public void setup() {
    storeService.delete();
    Model init = JenaUtils.read(new ClassPathResource("demo.ttl"));
    storeService.addData(init);
  }

  @Test
  public void simpleRead() {
    Assertions.assertTrue(askExists("http://demo.com/data/person/2"));
    Assertions.assertFalse(askExists("http://demo.com/data/person/3"));
  }

  @Test
  public void simpleWrite() {
    Assertions.assertFalse(askExists("http://demo.com/data/person/3"));
    insertType("http://demo.com/data/person/3", "http://demo.com/onto/Person");
    Assertions.assertTrue(askExists("http://demo.com/data/person/3"));
  }

  @Test
  public void rollback() {
    Assertions.assertFalse(askExists("http://demo.com/data/person/3"));
    try {
      transaction.write(() -> {
        insertType("http://demo.com/data/person/3", "http://demo.com/onto/Person");
        Assertions.assertTrue(askExists("http://demo.com/data/person/3"));
        insertType("<?s", "?o");
      });
    } catch (QueryParseException ignore) {

    }
    Assertions.assertFalse(askExists("http://demo.com/data/person/3"));
  }

  @Test
  public void rollbackAnnotated() {
    Assertions.assertFalse(askExists("http://demo.com/data/person/3"));
    try {
      testService.fail1();
    } catch (QueryParseException ignore) {

    }
    Assertions.assertFalse(askExists("http://demo.com/data/person/3"));
  }

  @Test
  public void writeInsideRead() {

    Assertions.assertFalse(askExists("http://demo.com/data/person/3"));
    Assertions.assertThrows(IllegalStateException.class, () -> testService.writeInsideRead(),
                            "WRITE transaction was nested inside READ transaction");
    Assertions.assertFalse(askExists("http://demo.com/data/person/3"));
  }

  private boolean askExists(String s) {
    String askQuery = "ASK { <" + s + "> ?p ?o }";
    return storeService.executeAskQuery(askQuery);
  }

  private void insertType(String s, String type) {
    String insertQuery ="INSERT DATA { <" + s + "> a <" + type + "> }";
    storeService.executeUpdateQuery(insertQuery);
  }

  @Test
  public void parallelReadTransactions() throws InterruptedException {

    CompletableFuture.supplyAsync(() -> {
      transaction.read(() -> {
        queryData();
        Try.run(() -> Thread.sleep(1000));
        queryData();
      });
      return null;
    }).whenComplete((r, e) -> {
      if (e != null) e.printStackTrace();
    });

    CompletableFuture.supplyAsync(() -> {
      transaction.read(() -> {
        queryData();
        throw new UnsupportedOperationException();
      });
      return null;
    }).whenComplete((r, e) -> {
      if (e != null) e.printStackTrace();
    });

    try {
      CompletableFuture.supplyAsync(() -> {
        transaction.read(() -> {
          queryData();
          throw new UnsupportedOperationException();
        });
        return null;
      }).whenComplete((r, e) -> {
        if (e != null) e.printStackTrace();
      }).get();
    }
    catch (ExecutionException e) {
      if (e.getCause() instanceof UnsupportedOperationException) {} //ignore
      else throw new IllegalStateException(e);
    }
  }

  private void queryData() {
    askExists("s");
  }

  @Test
  public void parallelWriteTransactions() throws InterruptedException {

    CompletableFuture.supplyAsync(() -> {
      transaction.write(() -> {
        writeData();
        Try.run(() -> Thread.sleep(1000));
        queryData();
      });
      return null;
    }).whenComplete((r, e) -> {
      if (e != null) e.printStackTrace();
    });

    CompletableFuture.supplyAsync(() -> {
      transaction.write(() -> {
        writeData();
        throw new UnsupportedOperationException();
      });
      return null;
    }).whenComplete((r, e) -> {
      if (e != null) e.printStackTrace();
    });

    try {
      CompletableFuture.supplyAsync(() -> {
        transaction.write(() -> {
          writeData();
          throw new UnsupportedOperationException();
        });
        return null;
      }).whenComplete((r, e) -> {
        if (e != null) e.printStackTrace();
      }).get();
    }
    catch (ExecutionException e) {
      if (e.getCause() instanceof UnsupportedOperationException) {} //ignore
      else throw new IllegalStateException(e);
    }
  }

  private void writeData() {
    insertType(UUID.randomUUID().toString(), "TEST");
  }


  @Service
  public static class TestService {
    private final RdfStoreService storeService;

    public TestService(RdfStoreService storeService) {
      this.storeService = storeService;
    }

    @ASquareTransactional(ASquareTransactionType.WRITE)
    public void fail1() {
      insertType("http://demo.com/data/person/3", "http://demo.com/onto/Person");
      Assertions.assertTrue(askExists("http://demo.com/data/person/3"));
      insertType("<?s", "?o");
    }

    @ASquareTransactional(ASquareTransactionType.READ)
    public void writeInsideRead() {
      insertType("http://demo.com/data/person/3", "http://demo.com/onto/Person");
    }


    private boolean askExists(String s) {
      String askQuery = "ASK { <" + s + "> ?p ?o }";
      return storeService.executeAskQuery(askQuery);
    }

    private void insertType(String s, String type) {
      String insertQuery ="INSERT DATA { <" + s + "> a <" + type + "> }";
      storeService.executeUpdateQuery(insertQuery);
    }
  }

  @Configuration
  @EnableAspectJAutoProxy
  @ComponentScan(basePackageClasses = TestService.class)
  public static class Config {

    private final Tdb2StoreService storeService;
    private final ASquareTransaction aSquareTransaction;
    private final ASquareTransactionalAspect aSquareTransactionalAspect;

    public Config() {
      storeService = Tdb2StoreService.inMemory(null);
      aSquareTransaction = storeService.getTransaction();
      aSquareTransactionalAspect = new ASquareTransactionalAspect(aSquareTransaction);
    }

    @Bean
    public RdfStoreService storeService() {
      return storeService;
    }

    @Bean
    public ASquareTransaction aSquareTransaction() {
      return aSquareTransaction;
    }

    @Bean
    public ASquareTransactionalAspect aSquareTransactionalAspect() {
      return aSquareTransactionalAspect;
    }

  }
}