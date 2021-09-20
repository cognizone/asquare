package zone.cogni.sem.jena.template;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

import static java.lang.System.currentTimeMillis;
import static org.apache.jena.query.QueryExecutionFactory.create;
import static org.slf4j.LoggerFactory.getLogger;
import static zone.cogni.sem.jena.template.JenaBooleanHandler.booleanAskResultExtractor;
import static zone.cogni.sem.jena.template.JenaQueryUtils.closeQuietly;

public class JenaQueryTemplate {

  public static final ModelCallback<Model> modelAsModel = model -> model;

  private static final Logger log = getLogger(JenaQueryTemplate.class);

  public static <T> T select(Model model, Query sparql, ListOfMapHandler<T> handler, boolean close) {
    return select(model, sparql, (QuerySolution) null, handler, close);
  }

  public static <T> T select(Model model, Query sparql, QuerySolution querySolution, ListOfMapHandler<T> handler) {
    return select(model, sparql, querySolution, handler, false);
  }

  public static <T> T select(Model model, Query sparql, QuerySolution querySolution, ListOfMapHandler<T> handler, boolean close) {
    long start = 0;
    if (log.isTraceEnabled()) {
      log.trace("Timing query [{}]", sparql);
      start = currentTimeMillis();
    }

    List<Map<String, RDFNode>> listOfBindings = select(model, sparql, querySolution, close);

    if (log.isTraceEnabled()) {
      log.trace("Execution time: '{}' ms.[{}]", currentTimeMillis() - start, sparql);
    }

    long time = 0;
    if (log.isTraceEnabled()) {
      time = currentTimeMillis();
    }

    T result = handler.handle(listOfBindings);

    if (log.isTraceEnabled()) {
      log.trace("Processing time: '{}' ms.[{}]", currentTimeMillis() - time, sparql);
      log.trace("TOTAL TIME(EXECUTION AND PROCESSING): '{}' ms.[{}]", currentTimeMillis() - start, sparql);
    }

    return result;
  }

  public static List<Map<String, RDFNode>> select(Model model, Query sparql, QuerySolution querySolution, boolean close) {
    return select(model, sparql, JenaResultSetHandlers.listOfMapsResolver, querySolution, close);
  }

  public static <T> T select(Model model, Query sparql, JenaResultSetHandler<T> resultSetResolver, QuerySolution querySolution, boolean close) {
    QueryExecution queryExecution = null;
    try {
      queryExecution = newQueryExecution(model, sparql, querySolution);

      long start = currentTimeMillis();
      if (log.isTraceEnabled()) {
        log.trace("EXECUTING SPARQL QUERY... [{}]", sparql);
      }

      ResultSet resultSet = queryExecution.execSelect();

      if (log.isTraceEnabled()) {
        log.trace("Select executed in '{}' ms. ...[{}]", currentTimeMillis() - start, sparql);
      }

      start = currentTimeMillis();

      T result = resultSetResolver.handle(resultSet);

      if (log.isTraceEnabled()) {
        log.trace("Resultset processed in '{}' ms. ...[{}]", currentTimeMillis() - start, sparql);
      }

      return result;
    }
    catch (Exception e) {
      throw new RuntimeException("Error while executing sparql query [" + sparql + "] : " + e.getMessage(), e);
    }
    finally {
      closeQuietly(queryExecution, close ? model : null);
    }
  }

  public static boolean ask(Model model, Query sparql) {
    return ask(model, sparql, false);
  }

  public static boolean ask(Model model, Query sparql, QuerySolution querySolution) {
    return ask(model, sparql, querySolution, booleanAskResultExtractor, false);
  }

  public static boolean ask(Model model, Query sparql, boolean close) {
    return ask(model, sparql, booleanAskResultExtractor, close);
  }

  public static <T> T ask(Model model, Query sparql, JenaBooleanHandler<T> booleanExtractor) {
    return ask(model, sparql, booleanExtractor, false);
  }

  public static <T> T ask(Model model, Query sparql, JenaBooleanHandler<T> booleanExtractor, boolean close) {
    return ask(model, sparql, null, booleanExtractor, close);
  }

  public static <T> T ask(Model model, Query sparql, QuerySolution querySolution, JenaBooleanHandler<T> booleanExtractor, boolean close) {
    QueryExecution queryExecution = null;
    try {
      queryExecution = newQueryExecution(model, sparql, querySolution);

      log.trace("EXECUTING SPARQL QUERY... [{}]", sparql);
      boolean result = queryExecution.execAsk();
      return booleanExtractor.handle(result);
    }
    catch (Exception e) {
      throw new RuntimeException("Error while executing sparql query [" + sparql + "] : " + e.getMessage());
    }
    finally {
      closeQuietly(queryExecution, close ? model : null);
    }
  }

  public static Model construct(Model model, Query sparql) {
    return construct(model, sparql, modelAsModel, false, false);
  }

  public static Model construct(Model model, Query sparql, QuerySolution initialBinding) {
    return construct(model, sparql, initialBinding, modelAsModel, false, false);
  }

  public static Model construct(Model model, Query sparql, boolean closeInput) {
    return construct(model, sparql, modelAsModel, false, closeInput);
  }

  public static Model construct(Model model, Query sparql, QuerySolution initialBinding, boolean closeInput) {
    return construct(model, sparql, initialBinding, modelAsModel, false, closeInput);
  }

  public static <T> T construct(Model model, Query sparql, ModelCallback<T> modelCallBack, boolean closeResult, boolean closeInput) {
    return construct(model, sparql, null, modelCallBack, closeResult, closeInput);
  }

  public static <T> T construct(Model model, Query sparql, QuerySolution initialBinding, ModelCallback<T> modelCallBack, boolean closeResult, boolean closeInput) {
    QueryExecution queryExecution = null;
    Model result = null;
    try {
      queryExecution = newQueryExecution(model, sparql, initialBinding);

      log.trace("EXECUTING SPARQL QUERY... [{}]", sparql);
      result = queryExecution.execConstruct();
      return modelCallBack.doInModel(result);
    }
    catch (Exception e) {
      throw new RuntimeException("Error while executing sparql query [" + sparql + "] : {}" + e.getMessage(), e);
    }
    finally {
      if (closeResult) closeQuietly(result);
      closeQuietly(queryExecution, closeInput ? model : null);
    }
  }

  public static Model construct(Model inputModel, Query sparql, Model resultModel, boolean closeInput) {
    QueryExecution queryExecution = null;
    try {
      queryExecution = newQueryExecution(inputModel, sparql);

      log.trace("EXECUTING SPARQL QUERY... [{}]", sparql);
      return queryExecution.execConstruct(resultModel);
    }
    catch (Exception e) {
      throw new RuntimeException("Error while executing sparql query [" + sparql + "] : " + e.getMessage(), e);
    }
    finally {
      closeQuietly(queryExecution, closeInput ? inputModel : null);

    }
  }

  public static QueryExecution newQueryExecution(Model model, Query sparql) {
    return newQueryExecution(model, sparql, null);
  }

  public static QueryExecution newQueryExecution(Model model, Query sparql, QuerySolution querySolution) {
    return create(sparql, model, querySolution);
  }
}
