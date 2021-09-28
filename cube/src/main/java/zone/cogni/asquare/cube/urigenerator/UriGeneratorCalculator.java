package zone.cogni.asquare.cube.urigenerator;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import zone.cogni.asquare.cube.spel.SpelService;
import zone.cogni.asquare.cube.urigenerator.json.UriGenerator;
import zone.cogni.asquare.cube.urigenerator.json.UriGeneratorRoot;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.triplestore.jenamemory.InternalRdfStoreService;
import zone.cogni.sem.jena.template.JenaResultSetHandlers;

import javax.annotation.Nonnull;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class UriGeneratorCalculator {
  private static final Logger log = LoggerFactory.getLogger(UriGeneratorCalculator.class);

  private final String newUriPrefix;
  private final SpelService spelService;
  private final UriGeneratorRoot uriGeneratorRoot;
  private final Map<String, Query> preparedStatements = new HashMap<>();

  public UriGeneratorCalculator(
    String newUriPrefix,
    SpelService spelService,
    Resource uriGeneratorRootResource
  ) {
    this.newUriPrefix = newUriPrefix;
    this.spelService = spelService;
    this.uriGeneratorRoot = UriGeneratorRoot.load(uriGeneratorRootResource);
    initPreparedStatements();
  }

  private void initPreparedStatements() {
    preparedStatements.put("exists-uri", QueryFactory.create("ask { { ?x ?p ?o } union { ?s ?p ?x } } "));
  }

  public String createTemporaryUri() {
    return newUriPrefix + "/" + UUID.randomUUID();
  }

  public boolean isNewUri(@Nonnull String uri) {
    return uri.startsWith(newUriPrefix);
  }

  public Model convert(Model model, Map<String, String> context) {
    RdfStoreService rdfStore = getRdfStore(model);
    List<UriGeneratorResult> results = getGeneratorResults(rdfStore);

    int replaceCount = inputValidations(model, results);
    processReplacements(model, rdfStore, replaceCount, context, results);

    validate(model);

    return model;
  }

  @Nonnull
  private List<UriGeneratorResult> getGeneratorResults(RdfStoreService rdfStore) {
    return uriGeneratorRoot.getGenerators()
                           .stream()
                           .map(generator -> getUriGeneratorResult(rdfStore, generator))
                           .collect(Collectors.toList());
  }

  @Nonnull
  private UriGeneratorResult getUriGeneratorResult(RdfStoreService rdfStore, UriGenerator generator) {
    UriGeneratorResult uriGeneratorResult = new UriGeneratorResult();
    uriGeneratorResult.setGenerator(generator);
    uriGeneratorResult.setUris(getNewSelectorUris(rdfStore, generator));
    return uriGeneratorResult;
  }

  /**
   * @return number of uris to be replaces
   */
  private int inputValidations(Model model, List<UriGeneratorResult> results) {
    Set<String> incomingUris = getProblemUris(model);

    Set<String> selectedUris = new HashSet<>();
    Set<String> duplicates = results.stream()
                                    .flatMap(result -> result.getUris().stream())
                                    .filter(uri -> !selectedUris.add(uri))
                                    .collect(Collectors.toSet());

    if (!duplicates.isEmpty())
      log.error("some uris matched multiple selectors: {}", duplicates);

    int size = incomingUris.size();
    if (size != selectedUris.size())
      log.error("incoming uris and selected uris do not match up." +
                "\n\t incoming: {}" +
                "\n\t selected: {}", incomingUris, selectedUris);

    if (!duplicates.isEmpty() || size != selectedUris.size())
      throw new RuntimeException("some validations failed when converting new uris, check logs for more info");


    log.info("(uri generator) replacing {} uris", size);
    return size;
  }

  private void processReplacements(Model model,
                                   RdfStoreService rdfStore,
                                   int replaceCount,
                                   Map<String, String> context,
                                   List<UriGeneratorResult> results) {
    int loopCount = 0;
    while (true) {
      int count = calculateReplacementUrisLoop(model, rdfStore, context, results);
      replaceCount -= count;

      log.info("(uri generator) loop {} processed {} uris", ++loopCount, count);

      // stop loop when all uris are processed
      if (replaceCount == 0) break;

      // stop loop when no replacement where processed
      if (count == 0) break;
    }
  }

  private int calculateReplacementUrisLoop(Model model,
                                           RdfStoreService rdfStore,
                                           Map<String, String> context,
                                           List<UriGeneratorResult> results) {
    AtomicInteger count = new AtomicInteger();

    results.forEach(result -> {
      result.getUris().forEach(uri -> {
        if (result.alreadyReplaced(uri)) return;

        Optional<String> possibleNewUri = calculateNewUri(rdfStore, context, result, uri);
        if (possibleNewUri.isPresent()) {
          count.addAndGet(1);
          result.addReplacement(uri, possibleNewUri.get());
          UriReplacement.replace(model, uri, possibleNewUri.get());
        }
      });
    });

    return count.get();
  }

  private Optional<String> calculateNewUri(RdfStoreService rdfStore,
                                           Map<String, String> context,
                                           UriGeneratorResult result,
                                           String oldUri) {
    if (log.isTraceEnabled()) log.trace("calculate new uri for {}", oldUri);
    traceModel(rdfStore);

    Map<String, String> variables = new HashMap<>(context);
    variables.put("uri", oldUri);

    // variable template can also NOT exist: then this step is skipped!
    String variableSelector = result.getGenerator().getFullVariableSelector();
    if (StringUtils.isNotBlank(variableSelector)) {
      String variableTemplateQuery = uriGeneratorRoot.getPrefixQuery() + variableSelector;
      String variableQuery = spelService.processTemplate(variableTemplateQuery, variables);
      if (log.isTraceEnabled()) log.trace("query: {}", variableQuery);

      Supplier<String> contextSupplier = () -> result.getGenerator().getId();
      Optional<Map<String, String>> variableMap = getQueryMap(contextSupplier, rdfStore, variableQuery);
      if (variableMap.isPresent() && variableMap.get().isEmpty()) return Optional.empty();

      Map<String, String> map = variableMap.get();
      if (log.isTraceEnabled()) log.trace("query result: {}", map);
      variables.putAll(map);
    }
    if (log.isTraceEnabled()) log.debug("variables: {}", variables);

    String uriTemplate = result.getGenerator().getUriTemplate();
    String newUri = spelService.processTemplate(uriTemplate, variables);

    if (existsInModel(rdfStore, newUri))
      throw new RuntimeException("uri overlap found for " + newUri);

    return Optional.of(newUri);
  }

  private void traceModel(RdfStoreService rdfStore) {
    if (!log.isTraceEnabled()) return;

    Model trace = rdfStore.executeConstructQuery("construct {?s ?p ?o} where {?s ?p ?o}");
    StringWriter out = new StringWriter();
    trace.write(out, "ttl");
    log.trace("model: {}", out);
  }

  private boolean existsInModel(RdfStoreService rdfStore, String newUri) {
    QuerySolutionMap querySolution = new QuerySolutionMap();
    querySolution.add("x", ResourceFactory.createResource(newUri));

    return rdfStore.executeAskQuery(preparedStatements.get("exists-uri"), querySolution);
  }

  /**
   * @return empty optional if one of resources start with a <code>newUriPrefix</code> !
   * else a map of variables to be used in uri template!
   */
  private Optional<Map<String, String>> getQueryMap(Supplier<String> context,
                                                    RdfStoreService rdfStore,
                                                    String variableQuery) {
    List<Map<String, RDFNode>> rows = queryForListOfMaps(rdfStore, variableQuery);
    if (rows.size() != 1)
      throw new RuntimeException("[" + context.get() + "] expected 1 row, found " + rows);
    Map<String, RDFNode> nodeMap = rows.get(0);
    boolean isBadMatch = nodeMap.values()
                                .stream()
                                .peek(node -> nonNullCheck(nodeMap, node))
                                .anyMatch(node -> node.isURIResource()
                                                  && node.asResource().getURI().startsWith(newUriPrefix));
    if (isBadMatch) return Optional.empty();

    Map<String, String> result = new HashMap<>();
    nodeMap.forEach((k, v) -> {
      result.put(k, (v.isResource() ? v.asResource().getURI() : v.asLiteral().getString()));
    });

    return Optional.of(result);
  }

  private void nonNullCheck(Map<String, RDFNode> nodeMap, RDFNode node) {
    if (node == null) throw new RuntimeException("variableSelector result has some null values: " + nodeMap);
  }

  private List<Map<String, RDFNode>> queryForListOfMaps(RdfStoreService rdfStore, String variableQuery) {
    try {
      return rdfStore.executeSelectQuery(variableQuery, JenaResultSetHandlers.listOfMapsResolver);
    }
    catch (RuntimeException e) {
      throw new RuntimeException("Query failed: \n" + variableQuery, e);
    }
  }

  private Set<String> getNewSelectorUris(RdfStoreService rdfStore, UriGenerator generator) {
    String query = uriGeneratorRoot.getPrefixQuery() + generator.getFullUriSelector();
    List<String> uris = getUris(rdfStore, query);
    return uris.stream()
               .filter(uri -> uri.startsWith(newUriPrefix))
               .collect(Collectors.toSet());
  }

  private List<String> getUris(RdfStoreService rdfStore, String query) {
    try {
      return rdfStore.executeSelectQuery(query, UriGeneratorCalculator::convertToList);
    }
    catch (RuntimeException e) {
      throw new RuntimeException("problem with query: \n" + query, e);
    }
  }

  private static List<String> convertToList(ResultSet resultSet) {
    List<String> result = new ArrayList<>();

    resultSet.forEachRemaining(querySolution -> {
      result.add(querySolution.get("uri").asResource().getURI());
    });

    return result;
  }

  private RdfStoreService getRdfStore(Model model) {
    return new InternalRdfStoreService(model);
  }

  private void validate(Model model) {
    Set<String> problemUris = getProblemUris(model);

    if (!problemUris.isEmpty()) throw new RuntimeException("some uris could not be replaced: " + problemUris);
  }

  @Nonnull
  private Set<String> getProblemUris(Model model) {
    Set<String> problemUris = new HashSet<>();
    model.listStatements()
         .forEachRemaining(statement -> {
           if (statement.getSubject().getURI().startsWith(newUriPrefix)) {
             problemUris.add(statement.getSubject().getURI());
           }

           if (statement.getObject().isURIResource()
               && statement.getObject().asResource().getURI().startsWith(newUriPrefix)) {
             problemUris.add(statement.getObject().asResource().getURI());
           }
         });
    return problemUris;
  }

}
