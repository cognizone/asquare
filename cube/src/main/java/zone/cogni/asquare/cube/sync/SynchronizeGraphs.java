package zone.cogni.asquare.cube.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import zone.cogni.asquare.cube.model2model.ModelToModel;
import zone.cogni.asquare.cube.monitoredpool.MonitoredPool;
import zone.cogni.asquare.cube.pagination.PaginatedQuery;
import zone.cogni.asquare.cube.sparql2json.PropertyConversion;
import zone.cogni.asquare.cube.spel.TemplateService;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.triplestore.jenamemory.InternalRdfStoreService;
import zone.cogni.core.spring.ResourceHelper;
import zone.cogni.core.util.function.CachingSupplier;
import zone.cogni.sem.jena.template.JenaResultSetHandlers;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class SynchronizeGraphs {

  private static final Logger log = LoggerFactory.getLogger(SynchronizeGraphs.class);

  /**
   * In configuration folder we require
   * <dl>
   *   <dt>select-graphs.sparql</dt>
   *   <dd>
   *     <p>
   *     Query with two columns: <b>graph</b> uris and <b>stamp</b> strings.
   *     Used for selection of relevant graphs in source and target triplestores.
   *     </p>
   *     <p></p>
   *   </dd>
   *
   *   <dt>select-stamp.sparql</dt>
   *   <dd>
   *     <p>
   *       Query which should return one column <b>stamp</b> with at most 1 row.
   *       Query for selection of <b>stamp</b> in case we only have graph.
   *     </p>
   *     <p></p>
   *   </dd>
   *
   *   <dt>update-source.sparql</dt>
   *   <dd>
   *     <p>
   *       Query which updates source with a <b>stamp</b> in case current source does not have a stamp.
   *       Requires <b>graph</b> and <b>stamp</b> parameters in SPeL.
   *     </p>
   *     <p>
   *       This must insert one "stamp" triple in source triplestore.
   *     </p>
   *     <p></p>
   *   </dd>
   *
   *   <dt>update-target.sparql</dt>
   *   <dd>
   *     <p>
   *     Query which updates target with a <b>stamp</b> since stamp is not in sync with the source stamp.
   *     Requires <b>stamp</b> parameter in SPeL.
   *     </p>
   *     <p>
   *       This will must insert and delete one "stamp" triple in target model.
   *       Full model will pushed to the target triplestore.
   *     </p>
   *     <p></p>
   *   </dd>
   *
   *   <dt>sync folder</dt>
   *   <dd>
   *     <p>
   *       Folder with a list of sparql constructs to create a new model which will be pushed to the target triplestore.
   *     </p>
   *     <p></p>
   *   </dd>
   * </dl>
   */
  private final String configurationFolder;
  private final RdfStoreService source;
  private final RdfStoreService target;
  private final Supplier<String> generateStamp;

  private final TemplateService templateService;
  private final PaginatedQuery paginatedQuery;
  private final MonitoredPool monitoredPool;

  public SynchronizeGraphs(String configurationFolder,
                           RdfStoreService source,
                           RdfStoreService target,
                           Supplier<String> generateStamp,
                           TemplateService templateService,
                           PaginatedQuery paginatedQuery,
                           MonitoredPool monitoredPool) {
    this.configurationFolder = calculateConfigurationFolder(configurationFolder);
    this.source = source;
    this.target = target;
    this.generateStamp = generateStamp;
    this.templateService = templateService;
    this.paginatedQuery = paginatedQuery;
    this.monitoredPool = monitoredPool;
  }

  private String calculateConfigurationFolder(String configurationFolder) {
    String result = StringUtils.removeEnd(configurationFolder, "/");

    ClassPathResource resource = new ClassPathResource(result);
    if (!resource.exists())
      throw new RuntimeException("Cannot find configuration folder '" + configurationFolder + "'.");
    if (isFile(resource))
      throw new RuntimeException("Configuration folder '" + configurationFolder + "' cannot be a file.");

    return result;
  }

  private boolean isFile(ClassPathResource resource) {
    try {
      return resource.isFile()                  // Spring is kinda misleading, we gotta dig deeper
             && resource.getFile().isFile();
    }
    catch (IOException e) {
      throw new RuntimeException("failure", e);
    }
  }

  public void synchronizeOne(String graphUri) {
    try {
      String defaultModificationStamp = generateStamp.get();
      Callable<String> syncCall = getSyncCallFromGraphUri(graphUri, defaultModificationStamp);
      syncCall.call();
    }
    catch (Exception e) {
      throw new RuntimeException("(synchronizeOne) failed", e);
    }
  }

  private Supplier<Model> getSourceModelSupplier(SelectedGraph selectedGraph) {
    return getSourceModelSupplier(selectedGraph.getGraphUri());
  }

  private Supplier<Model> getSourceModelSupplier(String graphUri) {
    return CachingSupplier.memoize(
            () -> paginatedQuery.getGraph(source, graphUri)
    );
  }

  private Supplier<SelectedGraph> getSelectedGraphFromModel(Supplier<Model> sourceModelSupplier, String graphUri) {
    return CachingSupplier.memoize(
            () -> {
              String stamp = getStamp(sourceModelSupplier.get(), graphUri);
              return new SelectedGraph(graphUri, stamp);
            });
  }

  private String getStamp(Model sourceModel, String graphUri) {
    try (RdfStoreService rdfStoreService = new InternalRdfStoreService(sourceModel)) {
      List<Map<String, RDFNode>> rows = rdfStoreService.executeSelectQuery(getQuery("select-stamp.sparql", ImmutableMap.of("graph", graphUri)), JenaResultSetHandlers.listOfMapsResolver);
      if (rows.size() > 1) throw new RuntimeException("found to many rows: \n" + rows);

      if (rows.isEmpty()) return null;
      Map<String, RDFNode> row = rows.get(0);

      if (row.size() != 1) throw new RuntimeException("found to many columns: \n" + row);
      RDFNode value = row.values().stream().findFirst().get();

      if (value.isResource()) throw new RuntimeException("expected a literal, found: " + value);
      return value.asLiteral().getString();
    }
  }

  private Model getCalculatedTargetModel(Model sourceModel) {
    return new ModelToModel(templateService, getSyncResources())
            .convert(sourceModel, "sync");
  }

  private Resource[] getSyncResources() {
    try {
      return new PathMatchingResourcePatternResolver()
              .getResources("classpath:" + configurationFolder + "/*/*.sparql");
    }
    catch (IOException e) {
      throw new RuntimeException("failed to fetch 'sync' resources", e);
    }
  }

  public void synchronizeMany(List<String> graphUris) {
    String defaultModificationStamp = generateStamp.get();
    List<Callable<String>> syncCalls = getSyncCallsFromGraphUri(graphUris, defaultModificationStamp);
    monitoredPool.invoke(syncCalls);
  }

  public void synchronize() {
    String defaultModificationStamp = generateStamp.get();

    Map<String, SelectedGraph> sourceMap = getSelectedGraphMap(source);
    Map<String, SelectedGraph> targetMap = getSelectedGraphMap(target);

    Collection<SelectedGraph> graphsToUpdate = getGraphsToUpdate(sourceMap, targetMap);
    Collection<Callable<String>> syncCalls = getSyncCallsFromSelectedGraphs(graphsToUpdate, defaultModificationStamp);

    Collection<String> graphsToRemove = getGraphsToRemove(sourceMap, targetMap);
    List<Callable<String>> deleteCalls = getDeleteCalls(graphsToRemove);

    Collection<Callable<String>> allCalls = CollectionUtils.union(deleteCalls, syncCalls);
    monitoredPool.invoke(allCalls);
  }

  private Collection<SelectedGraph> getGraphsToUpdate(Map<String, SelectedGraph> sourceMap,
                                                      Map<String, SelectedGraph> targetMap) {
    return sourceMap.values()
            .stream()
            .filter(sourceSelectGraph -> shouldUpdateTarget(sourceSelectGraph, targetMap))
            .collect(Collectors.toList());
  }

  private boolean shouldUpdateTarget(SelectedGraph sourceSelectGraph, Map<String, SelectedGraph> targetMap) {
    boolean noStamp = sourceSelectGraph.isMissingModificationStamp();
    if (noStamp) return true;

    SelectedGraph targetSelectGraph = targetMap.get(sourceSelectGraph.getGraphUri());
    if (targetSelectGraph == null) return true;

    boolean differentStamps = !Objects.equals(sourceSelectGraph.getModificationStamp(),
                                              targetSelectGraph.getModificationStamp());
    return differentStamps;
  }

  public void forceSynchronize() {
    String defaultModificationStamp = generateStamp.get();

    Map<String, SelectedGraph> sourceMap = getSelectedGraphMap(source);
    Map<String, SelectedGraph> targetMap = getSelectedGraphMap(target);

    Collection<SelectedGraph> graphsToUpdate = sourceMap.values();
    Collection<Callable<String>> syncCalls = getSyncCallsFromSelectedGraphs(graphsToUpdate, defaultModificationStamp);

    Collection<String> graphsToRemove = getGraphsToRemove(sourceMap, targetMap);
    List<Callable<String>> deleteCalls = getDeleteCalls(graphsToRemove);

    Collection<Callable<String>> allCalls = CollectionUtils.union(deleteCalls, syncCalls);
    monitoredPool.invoke(allCalls);
  }

  private Collection<String> getGraphsToRemove(Map<String, SelectedGraph> sourceMap, Map<String, SelectedGraph> targetMap) {
    return CollectionUtils.removeAll(targetMap.keySet(), sourceMap.keySet());
  }

  private Map<String, SelectedGraph> getSelectedGraphMap(RdfStoreService rdfStore) {
    List<Map<String, RDFNode>> rows = paginatedQuery.select(rdfStore, getQuery("select-graphs.sparql"));
    return rows.stream()
            .map(this::getSelectedGraph)
            .collect(Collectors.toMap(SelectedGraph::getGraphUri, Function.identity(), (a, b) -> a));
  }

  private SelectedGraph getSelectedGraph(Map<String, RDFNode> row) {
    boolean hasTwoColumns = row.size() != 2;
    boolean hasCorrectColumns = row.keySet().containsAll(Arrays.asList("graph", "stamp"));
    if (hasTwoColumns && hasCorrectColumns)
      throw new RuntimeException("expecting two columns 'graph' and 'stamp' in select-graph.sparql.");

    return new SelectedGraph(getStringValue(row, "graph"),
                             getStringValue(row, "stamp"));
  }

  private String getStringValue(Map<String, RDFNode> row, String column) {
    JsonNode stringNode = PropertyConversion.asString().apply(row.get(column));
    return stringNode == null ? null : stringNode.textValue();
  }

  private String getQuery(String query) {
    return ResourceHelper.toString(getQueryResource(query));
  }

  private String getQuery(String query, Object root) {
    return templateService.processTemplate(getQueryResource(query), root);
  }

  private ClassPathResource getQueryResource(String query) {
    ClassPathResource resource = new ClassPathResource(configurationFolder + "/" + query);
    if (!resource.exists()) throw new RuntimeException("Cannot find '" + resource + "'.");
    return resource;
  }

  private List<Callable<String>> getDeleteCalls(Collection<String> graphUris) {
    return graphUris.stream()
            .map(this::getDeleteCall)
            .collect(Collectors.toList());
  }

  private Callable<String> getDeleteCall(String graphUri) {
    return () -> {
      log.info("removing on target: {}", graphUri);
      target.deleteGraph(graphUri);

      return "[delete] " + graphUri;
    };
  }

  private List<Callable<String>> getSyncCallsFromGraphUri(Collection<String> graphUris, String defaultModificationStamp) {
    return graphUris.stream()
            .map(graphUri -> getSyncCallFromGraphUri(graphUri, defaultModificationStamp))
            .collect(Collectors.toList());
  }

  private Callable<String> getSyncCallFromGraphUri(String graphUri, String defaultModificationStamp) {
    Supplier<Model> sourceModelSupplier = getSourceModelSupplier(graphUri);
    Supplier<SelectedGraph> sourceSelectedGraphSupplier = getSelectedGraphFromModel(sourceModelSupplier, graphUri);
    return getSyncCall(defaultModificationStamp, sourceSelectedGraphSupplier, sourceModelSupplier);
  }

  private List<Callable<String>> getSyncCallsFromSelectedGraphs(Collection<SelectedGraph> graphsToUpdate,
                                                                String defaultModificationStamp) {
    return graphsToUpdate.stream()
            .map(selectedGraph -> getSyncCall(defaultModificationStamp,
                                              () -> selectedGraph,
                                              getSourceModelSupplier(selectedGraph)))
            .collect(Collectors.toList());
  }

  private Callable<String> getSyncCall(String defaultModificationStamp,
                                       Supplier<SelectedGraph> sourceSelectedGraphSupplier,
                                       Supplier<Model> sourceModelSupplier) {
    return () -> {
      SelectedGraph sourceSelectedGraph = sourceSelectedGraphSupplier.get();
      String graphUri = sourceSelectedGraph.getGraphUri();

      log.info("synchronizing {}", graphUri);

      if (isGraphDeleted(sourceModelSupplier)) {
        target.deleteGraph(sourceSelectedGraph.getGraphUri());
      }
      else {
        boolean sourceChanged = updateSourceGraph(sourceSelectedGraph, defaultModificationStamp);
        Supplier<Model> sourceModelSupplierToUse = sourceChanged ? getSourceModelSupplier(sourceSelectedGraph) : sourceModelSupplier;
        updateTargetGraph(sourceSelectedGraph, sourceModelSupplierToUse);
      }

      log.info("synchronizing {} done", graphUri);
      return "[sync  ] " + graphUri;
    };
  }

  private boolean isGraphDeleted(Supplier<Model> sourceModelSupplier) {
    return sourceModelSupplier.get().isEmpty();
  }

  private boolean updateSourceGraph(SelectedGraph sourceSelectedGraph, String defaultModificationStamp) {
    if (sourceSelectedGraph.hasModificationStamp()) return false; //no updates

    sourceSelectedGraph.setModificationStamp(defaultModificationStamp);
    addModificationStampToSource(sourceSelectedGraph);
    return true; //source data changed !
  }

  private void addModificationStampToSource(SelectedGraph selectedGraph) {
    String query = getQuery("update-source.sparql", asQueryContext(selectedGraph));
    source.executeUpdateQuery(query);
  }

  private void updateTargetGraph(SelectedGraph sourceSelectedGraph, Supplier<Model> sourceModelSupplier) {
    Model sourceModel = sourceModelSupplier.get();
    Model targetModel = getCalculatedTargetModel(sourceModel);

    // target stamp update or insert
    addModificationStampToTarget(targetModel, sourceSelectedGraph);

    target.replaceGraph(sourceSelectedGraph.getGraphUri(), targetModel);
  }

  private void addModificationStampToTarget(Model targetModel, SelectedGraph selectedGraph) {
    String query = getQuery("update-target.sparql", asQueryContext(selectedGraph));

    new InternalRdfStoreService(targetModel).executeUpdateQuery(query);
  }

  private Map<String, String> asQueryContext(SelectedGraph selectedGraph) {
    return ImmutableMap.of("graph", selectedGraph.getGraphUri(),
                           "stamp", selectedGraph.getModificationStamp());
  }
}
