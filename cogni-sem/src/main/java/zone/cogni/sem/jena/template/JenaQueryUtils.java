package zone.cogni.sem.jena.template;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.apache.jena.query.QueryFactory.create;
import static org.apache.jena.query.Syntax.syntaxARQ;


public abstract class JenaQueryUtils {

  public static List<Map<String, RDFNode>> convertToListOfMaps(ResultSet resultSet) {
    List<Map<String, RDFNode>> result = new ArrayList<>();


    while (resultSet.hasNext()) {
      QuerySolution querySolution = resultSet.next();
      result.add(convertRowToMap(resultSet.getResultVars(), querySolution));
    }

    return result;
  }

  // note: please keep resultVars !!
  private static Map<String, RDFNode> convertRowToMap(List<String> resultVars, QuerySolution querySolution) {
    Map<String, RDFNode> result = new HashMap<>();
    resultVars.forEach(var -> result.put(var, querySolution.get(var)));

    return result;
  }

  @Deprecated
  public static Map<String, RDFNode> convertRowToMap(QuerySolution querySolution) {
    Map<String, RDFNode> result = new HashMap<>();

    Iterator varNames = querySolution.varNames();
    while (varNames.hasNext()) {
      String varName = (String) varNames.next();
      result.put(varName, querySolution.get(varName));
    }

    return result;
  }

  public static QueryExecution newQueryExecution(Model model, String sparql) {
    return newQueryExecution(model, sparql, null);
  }

  public static QueryExecution newQueryExecution(Model model, Query sparql) {
    return newQueryExecution(model, sparql, null);
  }

  public static QueryExecution newQueryExecution(Model model, String sparql, QuerySolution querySolution) {
    return newQueryExecution(model, create(sparql, syntaxARQ), querySolution);
  }

  public static QueryExecution newQueryExecution(Model model, Query sparql, QuerySolution querySolution) {
    return QueryExecutionFactory.create(sparql, model, querySolution);
  }

  public static void closeQuietly(QueryExecution queryExecution) {
    try {
      if (queryExecution != null) queryExecution.close();
    }
    catch (Exception ignore) {
    }
  }

  public static void closeQuietly(Model model) {
    try {
      if (model != null) model.close();
    }
    catch (Exception ignore) {
    }
  }

  public static void closeQuietly(Graph graph) {
    try {
      if (graph != null) graph.close();
    }
    catch (Exception ignore) {
    }
  }

  public static void closeQuietly(QueryExecution queryExecution, Model model) {
    closeQuietly(queryExecution);
    closeQuietly(model);
  }

  public static void closeQuietly(Model model, Graph graph) {
    closeQuietly(model);
    closeQuietly(graph);
  }
}
