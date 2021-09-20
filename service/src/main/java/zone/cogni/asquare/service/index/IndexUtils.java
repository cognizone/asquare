package zone.cogni.asquare.service.index;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StopWatch;
import zone.cogni.asquare.rdf.ResultSetMapper;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.sem.jena.JenaUtils;
import zone.cogni.sem.jena.model.ResultSetDto;

import java.net.URL;
import java.util.concurrent.TimeUnit;

public class IndexUtils {

  public static final long BATCH_SIZE = 10000;

  private static final Logger log = LoggerFactory.getLogger(IndexUtils.class);

  public static String prettyDurationMs(long ms) {
    long second = TimeUnit.MILLISECONDS.toSeconds(ms);
    long minute = TimeUnit.MILLISECONDS.toMinutes(ms);
    long hour = TimeUnit.MILLISECONDS.toHours(ms);
    return String.format("%02d:%02d:%02d:%d",
                         hour,
                         minute - TimeUnit.HOURS.toMinutes(hour),
                         second - TimeUnit.MINUTES.toSeconds(minute),
                         ms - TimeUnit.SECONDS.toMillis(second));
  }

  public static String resolveTypeLocalNameByUri(String type) {
    try {
      // try to resolve local type for ontology URI
      URL url = new URL(type);
      url.toURI();
      return ResourceFactory.createResource(type).getLocalName();
    }
    catch (Exception exception) {
      // otherwise treat this uri as A2 alias
      return type;
    }
  }

  public static Model executePaginatedConstructQuery(RdfStoreService sparqlService, String sparqlQuery) {

    long offset = 0;
    boolean hasMoreValues = true;

    Model model = ModelFactory.createDefaultModel();
    StopWatch watch = new StopWatch("query " + sparqlQuery);
    watch.start();
    while (hasMoreValues) {
      String currentSparqlQuery = sparqlQuery + " LIMIT " + BATCH_SIZE + " OFFSET " + offset;
      Model currentModel = sparqlService.executeConstructQuery(currentSparqlQuery);

      if(currentModel.size() == 0) {
        hasMoreValues = false;
      }
      else {
        model.add(currentModel);
      }

      offset += BATCH_SIZE;
    }
    watch.stop();
    if (log.isDebugEnabled()) {
      log.info("Query executed  with {} results in: {}", model.size(), watch.shortSummary());
    }
    return model;
  }

  public static ResultSetDto executePaginatedQuery(RdfStoreService sparqlService, String sparqlQuery) {

    long offset = 0;
    boolean hasMoreValues = true;

    ResultSetDto resultSetDto = null;
    StopWatch watch = new StopWatch("query " + sparqlQuery);
    watch.start();
    while (hasMoreValues) {
      String currentSparqlQuery = sparqlQuery + " LIMIT " + BATCH_SIZE + " OFFSET " + offset;
      ResultSetDto currentResultSetDto = sparqlService.executeSelectQuery(currentSparqlQuery,
                                                                          ResultSetMapper::resultSetToResultSetDto);

      if (currentResultSetDto.getQuerySolutions().isEmpty()) {
        hasMoreValues = false;
      }
      if (resultSetDto == null) {
        resultSetDto = currentResultSetDto;
      }
      else {
        resultSetDto.getQuerySolutions().addAll(currentResultSetDto.getQuerySolutions());
      }
      offset += BATCH_SIZE;
    }
    watch.stop();
    if (log.isDebugEnabled()) {
      log.info("Query executed  with {} results in: {}", resultSetDto.getQuerySolutions().size(), watch.shortSummary());
    }
    return resultSetDto;
  }
}
