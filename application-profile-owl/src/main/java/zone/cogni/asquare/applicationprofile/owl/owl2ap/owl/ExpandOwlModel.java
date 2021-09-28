package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;

public class ExpandOwlModel implements Function<Model, Model> {

  private static final Logger log = LoggerFactory.getLogger(ExpandOwlModel.class);

  private Model model;

  private final SortedMap<String, Long> queryCount = new TreeMap<>();

  @Override
  public synchronized Model apply(Model model) {
    this.model = model;

    executeExpansion();
    executeCleanup();
    logSummary("Cleanup");

    reset();

    return model;
  }

  private void executeExpansion() {
    log.info("Starting expansion with triple count: {}", model.size());

    while (true) {
      long oldCount = getTotal();

      getFiles(new ClassPathResource("owl-expansion"))
              .forEach(this::executeExpansionQuery);

      long newCount = getTotal();

      log.info("New triples in cycle: {}", (newCount - oldCount));
      if (oldCount == newCount) break;
    }

    log.info("Finished expansion with triple count: {}", model.size());
    logSummary("Expansion");
  }

  private void executeExpansionQuery(File file) {
    long oldCount = model.size();

    Model queryModel = runQuery(file);
    Model resultingModel = ModelFactory.createUnion(this.model, queryModel);

    long newCount = resultingModel.size();

    updateCount(file, newCount - oldCount);

    model.add(queryModel);
  }

  private void executeCleanup() {
    log.info("Starting cleanup triple count: {}", model.size());

    getFiles(new ClassPathResource("owl-cleanup"))
            .forEach(this::executeCleanup);

    log.info("Final cleanup triple count: {}", model.size());
  }

  private void reset() {
    this.model = null;
    queryCount.clear();
  }



  private void executeCleanup(File file) {
    Model queryModel = runQuery(file);

    long oldSize = model.size();
    model.remove(queryModel);

    long newSize = model.size();
    updateCount(file, oldSize - newSize);
  }

  private void updateCount(File file, long appendSize) {
    long currentTotal = queryCount.getOrDefault(getQueryName(file), 0L);
    long newTotal = currentTotal + appendSize;
    queryCount.put(getQueryName(file), newTotal);
  }

  private Model runQuery(File file) {
    try {
      String query = getSparqlQuery(file);
      QueryExecution queryExecution = QueryExecutionFactory.create(QueryFactory.create(query), model);

      return queryExecution.execConstruct();
    }
    catch (Exception e) {
      throw new RuntimeException("Failed to run query " + file.getName(), e);
    }
  }

  private void logSummary(String message) {
    log.info("");
    log.info("----------          {}          ----------", message);
    log.info("");
    log.info("Query count summary");
    queryCount.forEach((query, count) -> {
      log.info("    {}: {}",
               StringUtils.rightPad(query, 44),
               StringUtils.leftPad(String.valueOf(count), 6));
    });
    log.info("");
  }

  private long getTotal() {
    return queryCount.values()
                     .stream()
                     .mapToLong(Long::longValue)
                     .sum();
  }



  private String getQueryName(File file) {
    return StringUtils.substringBefore(file.getName(), ".sparql");
  }

  private String getSparqlQuery(File file) {
    try {
      log.debug("Executing {}", getQueryName(file));
      return FileUtils.readFileToString(file, "UTF-8");
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private List<File> getFiles(ClassPathResource resource) {
    try {
      if (!resource.exists()) return new ArrayList<>();

      File[] files = resource.getFile().listFiles();
      if (files == null) return new ArrayList<>();

      return new ArrayList<>(Arrays.asList(files));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
