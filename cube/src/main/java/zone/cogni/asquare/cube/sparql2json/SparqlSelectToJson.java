package zone.cogni.asquare.cube.sparql2json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Streams;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.springframework.core.io.Resource;
import zone.cogni.asquare.cube.spel.TemplateService;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.triplestore.jenamemory.InternalRdfStoreService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SparqlSelectToJson {

  private final List<Query> queries;

  public SparqlSelectToJson(Resource[] queryResources, TemplateService templateService, Map<String, String> context) {
    this.queries = asQueries(queryResources, templateService, context);
  }

  private List<Query> asQueries(Resource[] queryResources, TemplateService templateService, Map<String, String> context) {
    return Arrays.stream(queryResources)
            .map(resource -> templateService.processTemplate(resource, context))
            .map(QueryFactory::create)
            .collect(Collectors.toList());
  }

  public ObjectNode convert(Model model, Map<String, RDFNode> bindings) {
    ObjectNode facet = JsonNodeFactory.instance.objectNode();
    return convert(facet, model, bindings);
  }

  public ObjectNode convert(ObjectNode facetNode, Model model, Map<String, RDFNode> bindings) {
    RdfStoreService rdfStore = new InternalRdfStoreService(model);
    return convert(facetNode, rdfStore, bindings);
  }

  public ObjectNode convert(ObjectNode facetNode, RdfStoreService rdfStore, Map<String, RDFNode> bindings) {
    QuerySolutionMap querySolutionMap = new QuerySolutionMap();
    bindings.forEach(querySolutionMap::add);

    queries.forEach(query -> addQueryData(facetNode, rdfStore, query, querySolutionMap));

    return facetNode;
  }

  private void addQueryData(ObjectNode facetNode, RdfStoreService rdfStore, Query query, QuerySolutionMap querySolutionMap) {
    List<QuerySolution> results = // ????
      rdfStore.executeSelectQuery(query, querySolutionMap, resultSet -> Streams.stream(resultSet)
                                                                               .collect(Collectors.toList()));
    if (results.isEmpty()) return;

    List<String> queryVariableNames = query.getResultVars();
    if (queryVariableNames.size() <= 1)
      throw new RuntimeException("Select query must specify at least one level and a value!");

    addQueryData(facetNode, queryVariableNames, results);
  }

  private void addQueryData(ObjectNode facetNode, List<String> queryVariableNames, List<QuerySolution> results) {
    PropertyConversion propertyConversion = getPropertyConversion(queryVariableNames);
    String valueVariable = propertyConversion.getPropertyName();

    results.forEach(result -> {
      RDFNode value = result.get(valueVariable);
      if (value == null) return;

      ObjectNode current = buildPath(facetNode, queryVariableNames, result);

      String levelBeforeValue = getLastLevelKey(queryVariableNames, result);

      addValue(current, levelBeforeValue, value, propertyConversion);
    });
  }

  private List<String> getPath(List<String> queryVariableNames, QuerySolution result) {
    return queryVariableNames.subList(0, queryVariableNames.size() - 2).stream()
                             .peek(varName -> checkIsLiteral(result, varName))
                             .map(varName -> result.getLiteral(varName).getLexicalForm())
                             .collect(Collectors.toList());
  }

  private PropertyConversion getPropertyConversion(List<String> varNames) {
    String lastVar = varNames.get(varNames.size() - 1);
    return PropertyConversion.fromName(lastVar);
  }

  private ObjectNode buildPath(ObjectNode facetNode, List<String> queryVariableNames, QuerySolution result) {
    List<String> path = getPath(queryVariableNames, result);

    ObjectNode current = facetNode;
    for (String level : path) {
      if (!current.has(level)) {
        ObjectNode newNode = JsonNodeFactory.instance.objectNode();
        current.set(level, newNode);
      }
      current = (ObjectNode) current.get(level);
    }
    return current;
  }

  private void addValue(ObjectNode current, String lastLevel, RDFNode value, PropertyConversion propertyConversion) {
    boolean isPresent = current.has(lastLevel);
    JsonNode convertedValue = propertyConversion.getConversion().apply(value);
    if (isPresent && propertyConversion.isList()) {
      ArrayNode array = (ArrayNode) current.get(lastLevel);
      array.add(convertedValue);
    }
    else if (isPresent && !propertyConversion.isList()) {
      throw new RuntimeException("multiple results found for " + propertyConversion.getPropertyName());
    }
    else if (!isPresent && propertyConversion.isList()) {
      ArrayNode array = JsonNodeFactory.instance.arrayNode();
      array.add(convertedValue);
      current.set(lastLevel, array);
    }
    else {
      current.set(lastLevel, convertedValue);
    }
  }

  private void checkIsLiteral(QuerySolution result, String varName) {
    if (!result.get(varName).isLiteral())
      throw new RuntimeException("Path variable " + varName + " is not a literal. All path variables should be literals.");
  }

  private String getLastLevelKey(List<String> queryVariableNames, QuerySolution result) {
    String varName = queryVariableNames.get(queryVariableNames.size() - 2);
    checkIsLiteral(result, varName);
    return result.get(varName).asLiteral().getLexicalForm();
  }
}

