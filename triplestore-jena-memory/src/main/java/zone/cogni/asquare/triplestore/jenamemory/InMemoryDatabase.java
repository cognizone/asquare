package zone.cogni.asquare.triplestore.jenamemory;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.shared.Lock;
import org.apache.jena.update.UpdateAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.sem.jena.template.JenaResultSetHandler;

import java.util.function.Supplier;

public class InMemoryDatabase implements RdfStoreService {

  private static final Logger log = LoggerFactory.getLogger(InMemoryDatabase.class);

//  private final Model model = ModelFactory.createDefaultModel();
//
//  private ResourcePatternResolver resourcePatternResolver;
//  private String preLoadLocations;

  private JenaModel jenaModel;

//  private String savePath;
//
//  private File storeFile;
//  private File tempStoreFile;

  public InMemoryDatabase() {
  }

//  public void setPreLoadLocations(String preLoadLocations, ResourcePatternResolver resourcePatternResolver) {
//    this.preLoadLocations = preLoadLocations;
//    this.resourcePatternResolver = resourcePatternResolver;
//  }

//  public void setSavePath(String savePath) {
//    this.savePath = savePath;
//  }


//  @PostConstruct
//  private void init() throws Exception {
//    if (StringUtils.isNotBlank(savePath)) {
//      storeFile = new File(savePath, "store.rdf");
//      tempStoreFile = new File(savePath, "temp-store.rdf");
//      storeFile.getParentFile().mkdirs();
//
//      if (storeFile.isFile()) JenaUtils.readInto(storeFile, model);
//    }
//
//    if (resourcePatternResolver == null || StringUtils.isBlank(preLoadLocations)) return;
//
//    Arrays.stream(StringUtils.split(preLoadLocations, ',')).forEach(location -> {
//      log.info("Loading RDF file {}.", location);
//      Arrays.stream(ResourceHelper.getResources(resourcePatternResolver, location)).forEach(resource -> {
//        try (InputStream inputStream = resource.getInputStream()) {
//          model.read(inputStream, null, JenaUtils.getLangByResourceName(location));
//        }
//        catch (IOException e) {
//          throw new RuntimeException(e);
//        }
//      });
//    });
//  }

  public JenaModel getJenaModel() {
    return jenaModel;
  }

  public void setJenaModel(JenaModel jenaModel) {
    this.jenaModel = jenaModel;
  }

  @Override
  public void addData(Model model) {
    executeInLock(Lock.READ, () -> jenaModel.get().add(model));
  }

  @Override
  public void addData(Model model, String graphUri) {
    throw new RuntimeException("Add data with graph not supported"); //or we add to default graph?
  }

  @Override
  public <R> R executeSelectQuery(Query query, QuerySolutionMap bindings, JenaResultSetHandler<R> resultSetHandler, String context) {
    return executeInLock(Lock.READ, () -> {

      if (log.isTraceEnabled()) log.trace("Select {} - {} \n{}",
                                          context == null ? "" : "--- " + context + " --- ",
                                          bindings,
                                          query);

      try (QueryExecution queryExecution = QueryExecutionFactory.create(query, jenaModel.get(), bindings)) {
        ResultSet resultSet = queryExecution.execSelect();
        return resultSetHandler.handle(resultSet);
      }
      catch (RuntimeException e) {
        log.error("Query failed: {}", query);
        throw e;
      }
    });
  }

  @Override
  public boolean executeAskQuery(Query query, QuerySolutionMap bindings) {
    return executeInLock(Lock.READ, () -> {
      try (QueryExecution queryExecution = QueryExecutionFactory.create(query, jenaModel.get(), bindings)) {
        return queryExecution.execAsk();
      }
      catch (RuntimeException e) {
        log.error("Query failed: {}", query);
        throw e;
      }
    });
  }

  @Override
  public Model executeConstructQuery(Query query, QuerySolutionMap bindings) {
    return executeInLock(Lock.READ, () -> {
      try (QueryExecution queryExecution = QueryExecutionFactory.create(query, jenaModel.get(), bindings)) {
        if (log.isTraceEnabled()) log.trace("Running construct query: \n{}", query);
        return queryExecution.execConstruct();
      }
      catch (RuntimeException e) {
        log.error("Query failed: {}", query);
        throw e;
      }
    });
  }

  @Override
  public void executeUpdateQuery(String updateQuery) {
    executeInLock(Lock.WRITE, () -> {
      UpdateAction.parseExecute(updateQuery, jenaModel.get());
//      if (null != storeFile) {
//        JenaUtils.write(model, tempStoreFile);
//        storeFile.delete();
//        tempStoreFile.renameTo(storeFile);
//      }
    });
  }

  @Override
  public void delete() {
    jenaModel.get().removeAll();
  }

  private void executeInLock(boolean lock, Runnable executeInLock) {
    jenaModel.get().enterCriticalSection(lock);
    try {
      executeInLock.run();
    }
    finally {
      jenaModel.get().leaveCriticalSection();
    }
  }

  private <T> T executeInLock(boolean lock, Supplier<T> executeInLock) {
    jenaModel.get().enterCriticalSection(lock);
    try {
      return executeInLock.get();
    }
    finally {
      jenaModel.get().leaveCriticalSection();
    }
  }
}
