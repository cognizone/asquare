package zone.cogni.asquare.cube.sparql2json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Streams;
import org.apache.commons.lang3.StringUtils;
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

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SparqlSelectToJson {

  public static class QueryContext {
    private Resource resource;
    private Map<String, String> parameters;

    private String queryString;
    private Query query;

    public QueryContext(Resource resource, Map<String, String> parameters) {
      this.resource = resource;
      this.parameters = parameters;
    }

    public Resource getResource() {
      return resource;
    }

    public void setResource(Resource resource) {
      this.resource = resource;
    }

    public Map<String, String> getParameters() {
      return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
      this.parameters = parameters;
    }

    public String getQueryString() {
      return queryString;
    }

    public void setQueryString(String queryString) {
      this.queryString = queryString;
    }

    public Query getQuery() {
      return query;
    }

    public void setQuery(Query query) {
      this.query = query;
    }
  }

  private final List<QueryContext> queries;

  public SparqlSelectToJson(Resource[] queryResources, TemplateService templateService, Map<String, String> context) {
    this.queries = asQueryContexts(queryResources, templateService, context);
  }

  private List<QueryContext> asQueryContexts(Resource[] queryResources, TemplateService templateService, Map<String, String> parameters) {
    List<QueryContext> result = new ArrayList<>();

    Arrays.stream(queryResources).forEach(queryResource -> {
      QueryContext queryContext = new QueryContext(queryResource, parameters);

      String queryString = templateService.processTemplate(queryContext.resource, queryContext.parameters);
      queryContext.setQueryString(queryString);

      Query query = QueryFactory.create(queryString);
      queryContext.setQuery(query);

      result.add(queryContext);
    });

    return result;
  }

  public ObjectNode convert(Model model, Map<String, RDFNode> bindings) {
    ObjectNode facet = JsonNodeFactory.instance.objectNode();
    return convert(facet, model, bindings);
  }

  public ObjectNode convert(ObjectNode facetNode, Model model, Map<String, RDFNode> bindings) {
    RdfStoreService rdfStore = new InternalRdfStoreService(model);

    QuerySolutionMap querySolutionMap = new QuerySolutionMap();
    bindings.forEach(querySolutionMap::add);

    queries.forEach(queryContext -> addQueryData(queryContext, facetNode, rdfStore, querySolutionMap));

    return facetNode;
  }

  private void addQueryData(QueryContext context, ObjectNode facetNode, RdfStoreService rdfStore, QuerySolutionMap querySolutionMap) {
    List<QuerySolution> results = // ????
            rdfStore.executeSelectQuery(context.query, querySolutionMap, resultSet -> Streams.stream(resultSet)
                                                                                             .collect(Collectors.toList()));
    if (results.isEmpty()) return;

    List<String> queryVariableNames = context.query.getResultVars();
    if (queryVariableNames.size() <= 1)
      throw new RuntimeException("Select query must specify at least one level and a value!");

    addQueryData(context, facetNode, queryVariableNames, results);
  }

  private void addQueryData(QueryContext context,
                            ObjectNode facetNode,
                            List<String> queryVariableNames,
                            List<QuerySolution> results) {
    PropertyConversion propertyConversion = getPropertyConversion(queryVariableNames);
    String valueVariable = propertyConversion.getPropertyName();

    results.forEach(result -> {
      RDFNode value = result.get(valueVariable);
      if (value == null) return;

      ObjectNode current = buildPath(facetNode, queryVariableNames, result);

      String levelBeforeValue = getLastLevelKey(context, queryVariableNames, result);

      addValue(context, current, levelBeforeValue, value, propertyConversion);
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

  private void addValue(QueryContext context,
                        ObjectNode current,
                        String lastLevel,
                        RDFNode value,
                        PropertyConversion propertyConversion) {
    boolean isPresent = current.has(lastLevel);
    JsonNode convertedValue = propertyConversion.getConversion().apply(value);
    if (isPresent && propertyConversion.isList()) {
      ArrayNode array = (ArrayNode) current.get(lastLevel);
      array.add(convertedValue);
    }
    else if (isPresent && !propertyConversion.isList()) {
      throw new RuntimeException("multiple results found, please define as 'xxxList':" +
                                 " property '" + propertyConversion.getPropertyName() + "'\n" +
                                 "\tquery: \n" + context.queryString);
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

  private String getLastLevelKey(@Nonnull QueryContext context,
                                 @Nonnull List<String> queryVariableNames,
                                 @Nonnull QuerySolution result) {
    String varName = queryVariableNames.get(queryVariableNames.size() - 2);
    checkIsLiteral(result, varName);
    String lastLevelKey = result.get(varName).asLiteral().getLexicalForm();
    if (StringUtils.isBlank(lastLevelKey))
      throw new RuntimeException("seems last key is empty for query: \n" + context.getQueryString());

    return lastLevelKey;
  }
}

