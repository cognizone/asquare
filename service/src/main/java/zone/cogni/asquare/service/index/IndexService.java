package zone.cogni.asquare.service.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.collections4.ListUtils;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;
import zone.cogni.asquare.access.Params;
import zone.cogni.asquare.service.async.AsyncTaskManager;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.sem.jena.model.ResultSetDto;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class IndexService {

  public static final String INDEX_TIMESTAMP_MS_NAME = "timestampms";
  public static final String INDEX_TIMESTAMP_PROPERTY_NAME = "lastIndexing";
  public static final String INDEX_GRAPH_NAME = "graph";
  private static final Logger log = LoggerFactory.getLogger(IndexService.class);
  private static final String GRAPH_PROP_NAME = "graph";
  private static final String URI_PROP_NAME = "uri";
  private static final String TYPE_PROP_NAME = "type";
  private static final String INDEX_PROP_NAME = "index";
  private static final int INDEX_TYPE_ATTEMPTS = 5;
  private final GraphIndexService graphIndexService;
  private final IndexConfigProvider indexConfigProvider;
  private final String configIndexName;

  private final AsyncTaskManager indexingGraphTaskExecutor;
  private final AsyncTaskManager indexingTaskExecutor;
  private final Long iterationTimeoutMs;

  public IndexService(
          IndexConfigProvider indexConfigProvider,
          @Qualifier("indexingGraphTaskExecutor") AsyncTaskManager indexingGraphTaskExecutor,
          @Qualifier("indexingTaskExecutor") AsyncTaskManager indexingTaskExecutor,
          @Value("${index.iterationTimeoutMs:3600000}") Long iterationTimeoutMs,
          @Value("${asquare.ap.config:config}") String configIndexName,
          GraphIndexService graphIndexService) {

    this.configIndexName = configIndexName;
    this.iterationTimeoutMs = iterationTimeoutMs;
    this.indexingGraphTaskExecutor = indexingGraphTaskExecutor;
    this.indexingTaskExecutor = indexingTaskExecutor;
    this.graphIndexService = graphIndexService;
    this.indexConfigProvider = indexConfigProvider;
  }

  public List<ResourceIndex> findAllIndexResources(String sparqlQuery, RdfStoreService rdfStoreService) {
    ResultSetDto resultSetDto = IndexUtils.executePaginatedQuery(rdfStoreService, sparqlQuery);

    List<String> propertiesList = resultSetDto.getVars();
    if (!propertiesList.containsAll(ImmutableList.of(GRAPH_PROP_NAME, URI_PROP_NAME, TYPE_PROP_NAME, INDEX_PROP_NAME))) {
      throw new IndexException("Query results doesn't contain all required fields: {}, {}, {}, {}", GRAPH_PROP_NAME, URI_PROP_NAME, TYPE_PROP_NAME, INDEX_PROP_NAME);
    }

    return resultSetDto.stream().map(qs -> ResourceIndex.create(qs.getProperty(GRAPH_PROP_NAME),
                                                                qs.getProperty(URI_PROP_NAME),
                                                                qs.getProperty(TYPE_PROP_NAME),
                                                                qs.getProperty(INDEX_PROP_NAME))).collect(Collectors.toList());
  }

  public Boolean isAsyncIndexBusy() {
    return indexingTaskExecutor.isBusy();
  }

  @Deprecated
  @Async("indexingTaskExecutor")
  public void reindexAsync(String sparqlQuery) {
    reindexSync(sparqlQuery);
  }

  @Deprecated
  @Async("indexingTaskExecutor")
  public void reindexAsync(String[] sparqlQueries) {
    reindexSync(sparqlQueries);
  }

  @Async("indexingTaskExecutor")
  public void reindexAsync(RdfStoreService rdfStoreService, String sparqlQuery) {
    reindexSync(rdfStoreService, sparqlQuery);
  }

  @Async("indexingTaskExecutor")
  public void reindexAsync(RdfStoreService rdfStoreService, String[] sparqlQueries) {
    reindexSync(rdfStoreService, sparqlQueries);
  }

  @Deprecated
  public void reindexSync(String[] sparqlQueries) {
    for (String sparqlQuery : sparqlQueries) {
      reindexSync(sparqlQuery);
    }
  }

  public void reindexSync(RdfStoreService rdfStoreService, String[] sparqlQueries) {
    for (String sparqlQuery : sparqlQueries) {
      reindexSync(rdfStoreService, sparqlQuery);
    }
  }

  @Deprecated
  public Map<String, Long> reindexSync(String sparqlQuery) {
    return reindexSync(indexConfigProvider.getRdfStoreService(), sparqlQuery, null);
  }

  public Map<String, Long> reindexSync(RdfStoreService rdfStoreService, String sparqlQuery) {
    return reindexSync(rdfStoreService, sparqlQuery, null);
  }

  @Deprecated
  public Map<String, Long> reindexSync(String sparqlQuery, Consumer<List<ResourceIndex>> preProcessing) {
    return reindexSync(indexConfigProvider.getRdfStoreService(), sparqlQuery, preProcessing);
  }

  public Map<String, Long> reindexSync(String graphUri, List<String> sparqlQueries, RdfStoreService rdfStoreService, Consumer<List<ResourceIndex>> preProcessing) {
    log.info("Launching reindexing for graph {}", graphUri);

    // load graph to index in a Model

    Model model = IndexUtils.executePaginatedConstructQuery(rdfStoreService, "select * { graph <" + graphUri + "> { ?x ?y ?z } }");
    log.info("Loaded graph with {} statements", model.size());

    //  create RdfStoreService for Dataset with this graph as a model
    Dataset dataset = DatasetFactory.create();
    dataset.addNamedModel(graphUri, model);
    DatasetRdfStoreService datasetRdfStoreService = new DatasetRdfStoreService(dataset);

    // run index selection queries (same as indexAll)
    List<ResourceIndex> resources = sparqlQueries.stream().flatMap(sparqlQuery -> findAllIndexResources(sparqlQuery, rdfStoreService).stream()).distinct().collect(Collectors.toList());

    log.info("Queries processed with {} results", resources.size());

    List<String> indexListToDelete = resources.stream().map(ResourceIndex::getIndex).distinct().collect(Collectors.toList());

    // delete all documents matching this graph in elastic
    try {
      ObjectNode deleteQuery = (ObjectNode) (new ObjectMapper()).readTree(
              "{" +
              "  \"query\":{" +
              "    \"bool\":{" +
              "      \"must\":[{ " +
              "        \"match\": {" +
              "           \"graph\":\"" + graphUri + "\"" +
              "          }" +
              "        }" +
              "      ]" +
              "    }" +
              "  }" +
              "}");

      for (String indexName : indexListToDelete) {
        indexConfigProvider.getElasticStore().deleteByQuery(indexName, deleteQuery);
      }
    }
    catch (IOException e) {
      log.error("Can not remove indexed graph {}", graphUri);
      return ImmutableMap.of();
    }

    // run index for all selected results
    return resources.size() > 0 ? reindexSync(resources, datasetRdfStoreService, preProcessing, model) : ImmutableMap.of();
  }

  public void garbageCollect(List<String> sparqlQueries, RdfStoreService rdfStoreService, Consumer<List<ResourceIndex>> preProcessing, long latestTimestamp) {
    List<ResourceIndex> resources = sparqlQueries.stream().flatMap(sparqlQuery -> findAllIndexResources(sparqlQuery, rdfStoreService).stream()).distinct().collect(Collectors.toList());
    List<String> indexListToDelete = resources.stream().map(ResourceIndex::getIndex).distinct().collect(Collectors.toList());

    // delete all documents matching this graph in elastic
    try {
      String query = "{" +
                     "  \"query\": {" +
                     "    \"bool\": {" +
                     "      \"must\": [" +
                     "        {" +
                     "          \"range\": {" +
                     "            \"" + INDEX_TIMESTAMP_MS_NAME + "\": {" +
                     "              \"lt\": " + latestTimestamp + "" +
                     "            }" +
                     "          }" +
                     "        }" +
                     "      ]" +
                     "    }" +
                     "  }" +
                     "}";

      ObjectNode deleteQuery = (ObjectNode) (new ObjectMapper()).readTree(query);

      for (String indexName : indexListToDelete) {
        log.info("GC preparing to process index {} with query {}", indexName, query);
        ObjectNode ack = indexConfigProvider.getElasticStore().deleteByQueryWithAck(indexName, deleteQuery, Params.waitFor());
        if (ack != null) {
          log.info("GC processed index {} with acknowledgement {}", indexName, ack);
        }
      }
    }
    catch (IOException e) {
      log.error("Can not collect garbage: ", e);
    }

  }

  public void garbageCollect(List<String> sparqlQueries, RdfStoreService rdfStoreService, Consumer<List<ResourceIndex>> preProcessing) {
    try {
      ObjectNode indexingTimestamp = indexConfigProvider.getElasticStore().getDocumentById(configIndexName, INDEX_TIMESTAMP_PROPERTY_NAME);
      if (indexingTimestamp != null) {
        JsonNode timestampNode = indexingTimestamp.get("_source").get(INDEX_TIMESTAMP_MS_NAME);
        if (timestampNode instanceof LongNode) {
          garbageCollect(sparqlQueries, rdfStoreService, preProcessing, timestampNode.longValue());
        }
        else {
          log.error("Indexing timestamp has unexpected format {}", timestampNode);
        }
      }
    }
    catch (Exception ex) {
      log.error("Can not collect garbage using index {} {}", configIndexName, ex);
    }
  }

  public void updateReindexTimestamp(long timestamp) {
    log.info("Requested reindex timestamp update {}", timestamp);
    try {
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode indexingTimestamp = (ObjectNode) mapper.readTree("{\"" + INDEX_TIMESTAMP_MS_NAME + "\":" + timestamp + "}");
      indexConfigProvider.getElasticStore().indexDocument(configIndexName, INDEX_TIMESTAMP_PROPERTY_NAME, indexingTimestamp);
      log.info("Reindex timestamp created {}", timestamp);
    }
    catch (Exception ex) {
      log.error("Can not write indexing timestamp", ex);
    }
  }

  private Map<String, Long> reindexSync(List<ResourceIndex> resources, RdfStoreService rdfStoreService, Consumer<List<ResourceIndex>> preProcessing, Model graphModel) {

    StopWatch watch = new StopWatch();
    watch.start();

    if (preProcessing != null) {
      preProcessing.accept(resources);
    }
    List<ResourceIndex> failedResources = Collections.synchronizedList(new ArrayList<>());

    long total = resources.size();
    long indexed = 0;

    long timestamp = ZonedDateTime.now().toInstant().toEpochMilli();

    for (int i = 0; !resources.isEmpty() && i < INDEX_TYPE_ATTEMPTS; i++) {
      log.info("Indexing {} resources attempt {} with timestamp {}", resources.size(), i, timestamp);

      reindexIteration(resources, failedResources, rdfStoreService, graphModel, timestamp);

      indexed += resources.size() - failedResources.size();

      resources = failedResources;
      failedResources = Collections.synchronizedList(new ArrayList<>());
    }

    long failed = resources.size();
    if (failed > 0) {
      for (ResourceIndex failedResource : resources) {
        log.error("Resource {} of type {} from graph {} failed all reindex attemts.",
                  failedResource.getUri(), failedResource.getType(), failedResource.getGraph());
      }
    }

    watch.stop();

    if (log.isInfoEnabled()) {
      log.info("Reindex summary. Total: {}. Indexed: {}. Failed: {}. Time spent: {}", total, indexed, failed, watch.shortSummary());
    }
    return ImmutableMap.of("total", total, "indexed", indexed, "failed", failed, "duration", watch.getTotalTimeMillis());
  }

  public Map<String, Long> reindexSync(RdfStoreService rdfStoreService, String sparqlQuery, Consumer<List<ResourceIndex>> preProcessing) {
    log.info("Launching reindexing for query {}", sparqlQuery);
    List<ResourceIndex> resources = Collections.synchronizedList(findAllIndexResources(sparqlQuery, rdfStoreService));
    log.info("Query finished with {} results", resources.size());
    return reindexSync(resources, rdfStoreService, preProcessing, null);
  }

  private void reindexIteration(List<ResourceIndex> resources, List<ResourceIndex> failedResources, RdfStoreService rdfStoreService, Model graphModel, long timestamp) {

    long startIteration = System.currentTimeMillis();

    LinkedHashMap<String, List<ResourceIndex>> resourcesByGraph = groupByGraph(resources);

    log.info("Starting iteration. Found {} resources in {} graphs", resources.size(), resourcesByGraph.size());

    for (String graph : resourcesByGraph.keySet()) {
      try {
        indexingGraphTaskExecutor.awaitPoolIsReady();//wait for free slot in graph indexing pool
        log.info("Starting graph indexing {}", graph);

        graphIndexService.indexGraphAsync(graph, resourcesByGraph.get(graph), failedResources, Params.noRefresh().withTimestamp(timestamp), rdfStoreService, graphModel);
      }
      catch (Exception ex) {
        log.info("Graph {} indexing is failed. Adding to list of failed resources.", graph, ex);
        failedResources.addAll(resourcesByGraph.get(graph));
      }
    }
    String runningTasksStr = String.join(", ", indexingGraphTaskExecutor.getExecutionKeysAsStrings());
    log.info("Waiting to stop iteration. Indexing is still busy with {}", runningTasksStr);
    List<String> interruptedGraphs = indexingGraphTaskExecutor.awaitBusyWithNotMoreAndNoLongerThan(0, iterationTimeoutMs);//wait until all graph indexing tasks are done
    if (interruptedGraphs.size() > 0) {
      interruptedGraphs.forEach(graphUri -> log.warn("Graph {} failed timeout", graphUri));
      List<ResourceIndex> tmpResources = resources
              .stream()
              .filter(resource -> interruptedGraphs.stream().noneMatch(interruptedGraph -> resource.getGraph().equals(interruptedGraph))).collect(Collectors.toList());
      resources.clear();
      resources.addAll(tmpResources);
    }
    // timeout resources are not repeated
    // interruptedGraphs.stream().map(resourcesByGraph::get).forEach(failedResources::addAll);
    log.info("Indexing iteration is done. Duration: {}, failed resources: {}", IndexUtils.prettyDurationMs(System.currentTimeMillis() - startIteration), failedResources.size() + interruptedGraphs.size());
  }

  //still group by graph but we keep the original order, some indexings need that
  private LinkedHashMap<String, List<ResourceIndex>> groupByGraph(List<ResourceIndex> resources) {
    return resources
            .stream()
            .collect(Collectors.toMap(ResourceIndex::getGraph, Arrays::asList, ListUtils::union, LinkedHashMap::new));
  }

  /**
   * This method is an alternative to reindexSync/reindexAsync
   * but leaves the control of the sync/async selection,
   * as well as the executor, to the code that uses it.
   *
   * @param rdfStoreService the service used for the desired triple store
   * @param sparqlQuery     the SPARQL query that selects the resources to be indexed
   * @return Map<String, Long> with information about the indexing results
   */
  public Map<String, Long> reindexSimple(RdfStoreService rdfStoreService, String sparqlQuery) {
    log.info("Launching reindexing for query {}", sparqlQuery);
    List<ResourceIndex> resources = Collections.synchronizedList(findAllIndexResources(sparqlQuery, rdfStoreService));
    log.info("Query finished with {} results", resources.size());
    return reindexSimpleExecution(resources, rdfStoreService);
  }

  private Map<String, Long> reindexSimpleExecution(List<ResourceIndex> resources, RdfStoreService rdfStoreService) {

    StopWatch watch = new StopWatch();
    watch.start();

    List<ResourceIndex> failedResources = Collections.synchronizedList(new ArrayList<>());

    long total = resources.size();
    long indexed = 0;

    long timestamp = ZonedDateTime.now().toInstant().toEpochMilli();

    for (int i = 0; !resources.isEmpty() && i < INDEX_TYPE_ATTEMPTS; i++) {
      log.info("Indexing {} resources attempt {} with timestamp {}", resources.size(), i, timestamp);

      reindexSimpleIteration(resources, failedResources, rdfStoreService, timestamp);

      indexed += resources.size() - failedResources.size();

      resources = failedResources;
      failedResources = Collections.synchronizedList(new ArrayList<>());
    }

    long failed = resources.size();
    if (failed > 0) {
      for (ResourceIndex failedResource : resources) {
        log.error("Resource {} of type {} from graph {} failed all reindex attempts.",
                  failedResource.getUri(), failedResource.getType(), failedResource.getGraph());
      }
    }

    watch.stop();

    if (log.isInfoEnabled()) {
      log.info("Reindex summary. Total: {}. Indexed: {}. Failed: {}. Time spent: {}", total, indexed, failed, watch.shortSummary());
    }
    return ImmutableMap.of("total", total, "indexed", indexed, "failed", failed, "duration", watch.getTotalTimeMillis());
  }

  private void reindexSimpleIteration(List<ResourceIndex> resources, List<ResourceIndex> failedResources, RdfStoreService rdfStoreService, long timestamp) {

    long startIteration = System.currentTimeMillis();

    LinkedHashMap<String, List<ResourceIndex>> resourcesByGraph = groupByGraph(resources);

    log.info("Starting iteration. Found {} resources in {} graphs", resources.size(), resourcesByGraph.size());

    for (String graph : resourcesByGraph.keySet()) {
      try {
        log.info("Starting graph indexing {}", graph);

        graphIndexService.indexGraphSync(graph, resourcesByGraph.get(graph), failedResources, Params.noRefresh().withTimestamp(timestamp), rdfStoreService, null);
      }
      catch (Exception ex) {
        log.info("Graph {} indexing is failed. Adding to list of failed resources.", graph, ex);
        failedResources.addAll(resourcesByGraph.get(graph));
      }
    }
    log.info("Indexing iteration is done. Duration: {}, failed resources: {}", IndexUtils.prettyDurationMs(System.currentTimeMillis() - startIteration), failedResources.size());
  }

}
