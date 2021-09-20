package zone.cogni.asquare.service.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Streams;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Resource;
import org.elasticsearch.index.IndexNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import zone.cogni.asquare.access.ApplicationView;
import zone.cogni.asquare.access.ElasticAccessService;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.rdf.TypedResource;
import zone.cogni.asquare.service.async.AsyncTaskManager;
import zone.cogni.asquare.service.elasticsearch.v7.GenericElastic7Configuration;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TypeIndexService {

  private static final Logger log = LoggerFactory.getLogger(TypeIndexService.class);

  private final IndexService indexService;
  private final IndexConfigProvider indexConfigProvider;
  private final GraphIndexService graphIndexService;
  private final AsyncTaskManager indexingTaskExecutor;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public TypeIndexService(IndexConfigProvider indexConfigProvider,
                          @Qualifier("indexingTaskExecutor") AsyncTaskManager indexingTaskExecutor,
                          GraphIndexService graphIndexService,
                          IndexService indexService) {
    this.indexConfigProvider = indexConfigProvider;
    this.indexService = indexService;
    this.indexingTaskExecutor = indexingTaskExecutor;
    this.graphIndexService = graphIndexService;
  }

  @Async("indexingTaskExecutor")
  public void resetIndexAndReindexTypesAsync(String[] typeUrisToIndex, String indexName) {
    resetIndexAndReindexTypesSync(typeUrisToIndex, indexName);
  }

  @Async("indexingTaskExecutor")
  public void deleteAndReindexTypesAsync(String[] typeUrisToIndex, String indexName) {
    deleteAndReindexTypesSync(typeUrisToIndex, indexName);
  }

  @Async("indexingTaskExecutor")
  public void reindexTypesAsync(String[] typeUrisToIndex, String indexName) {
    reindexTypesSync(typeUrisToIndex, indexName);
  }

  @Async("indexingTaskExecutor")
  public void deleteAndReindexTypeAsync(String typeUriToIndex, String indexName) {
    deleteAndReindexTypeSync(typeUriToIndex, indexName);
  }

  @Async("indexingTaskExecutor")
  public void reindexTypeAsync(String typeUriToIndex, String indexName) {
    reindexTypeSync(typeUriToIndex, indexName);
  }

  public void resetIndexAndReindexTypesSync(String[] typesToIndex, String indexName) {
    ResourceIndex resourceIndex = ResourceIndex.create(StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY, indexName);
    resetIndex(indexName, GenericElastic7Configuration.getSimpleSettings());
    reindexTypesSync(typesToIndex, indexName);
  }

  public void resetIndexAndReindexTypesSync(String[] typesToIndex, String indexName, ObjectNode indexSettings) {
    ResourceIndex resourceIndex = ResourceIndex.create(StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY, indexName);
    resetIndex(indexName, indexSettings);
    reindexTypesSync(typesToIndex, indexName);
  }

  public void resetIndex(String indexName, ObjectNode indexSettings) {
    log.info(".. resetting index '{}' ...", indexName);
    try {
      indexConfigProvider.getElasticStore().deleteIndex(indexName);
      log.info(".. index '{}' deleted", indexName);
    }
    catch(IndexNotFoundException ex) {
      log.info(".. index '{}' not found", indexName);
    }
    indexConfigProvider.getElasticStore().createIndex(indexName, indexSettings);
    log.info(".. index '{}' created", indexName);
  }

  public void deleteAndReindexTypesSync(String[] typeUrisToIndex, String indexName) {
    for (String typeUriToIndex : typeUrisToIndex) {
      deleteAndReindexTypeSync(typeUriToIndex, indexName);
    }
  }

  public void reindexTypesSync(String[] typeUrisToIndex, String indexName) {
    for (String typeUriToIndex : typeUrisToIndex) {
      reindexTypeSync(typeUriToIndex, indexName);
    }
  }

  public void deleteAndReindexTypeSync(String typeUriToIndex, String indexName) {
    deleteType(typeUriToIndex, indexName);
    reindexTypeSync(typeUriToIndex, indexName);
  }

  public void reindexTypeSync(String typeUriToIndex, String indexName) {
    indexService.reindexSync("select ?graph ?uri (<" + typeUriToIndex + "> as ?type) ('" + indexName + "' as ?index) {\n" +
                             "  graph ?graph {\n" +
                             "    ?uri a <" + typeUriToIndex + "> \n" +
                             "  }\n" +
                             "}");
  }

  public Boolean isAsyncIndexBusy() {
    return indexingTaskExecutor.isBusy();
  }

  public Boolean indexGraphResource(ResourceIndex resourceIndex) {
    return graphIndexService.indexUriSync(resourceIndex);
  }

  public Boolean indexGraphResource(String uri, String typeUri, String indexName, String graphUri) {
    ResourceIndex resourceIndex = ResourceIndex.create(graphUri, uri, typeUri, indexName);
    return graphIndexService.indexUriSync(resourceIndex);
  }

  public Boolean indexGraphResource(String uri, String typeUri, String indexName) {
    String graphUri = findGraphUri(uri);
    return indexGraphResource(uri, typeUri, indexName, graphUri);
  }

  public void deleteType(String typeUri, String indexName) {
    ObjectNode objectNode = objectMapper.createObjectNode();
    objectNode
      .putObject("query")
      .putObject("match")
      .put("data.type.keyword", IndexUtils.resolveTypeLocalNameByUri(typeUri));
    indexConfigProvider.getElasticStore().deleteByQuery(indexName, objectNode);
  }

  public void resetIndex(String indexName) {
    resetIndex(indexName, GenericElastic7Configuration.getSimpleSettings());
  }

  public void deleteResource(String resourceUri, String typeUri, String indexName) {
    String graphUri = findGraphResourceUri(resourceUri);
    ResourceIndex resourceIndex = ResourceIndex.create(graphUri, resourceUri, typeUri, indexName);
    ApplicationView view = graphIndexService.createApplicationView(resourceIndex, indexConfigProvider.getRdfStoreService(), null);
    String localTypeName = IndexUtils.resolveTypeLocalNameByUri(typeUri);
    ApplicationProfile.Type type = view.getApplicationProfile().getType(localTypeName);
    TypedResource resource = view.find(() -> type, resourceIndex.getUri());
    deleteGraphResource(indexName, resource);
  }

  public void deleteResource(TypedResource resource, String typeUri, String indexName) {
    String uri = resource.getResource().getURI();
    String graphUri = findGraphResourceUri(uri);
    deleteGraphResource(indexName, resource);
  }

  private void deleteGraphResource(String indexName, TypedResource resource) {
    log.info("Clear {} '{}' from Elastic", resource.getType().getClassId(), resource.getResource().getURI());
    indexConfigProvider.getElasticStore().deleteDocument(indexName, resource.getResource().getURI());
  }

  public String findGraphUri(String instanceUri) {
    String selectGraphQuery = "select distinct ?graph { " +
                              "  GRAPH ?graph { " +
                              "    <" + instanceUri + "> ?p ?o." +
                              "  } " +
                              "} limit 1";

    List<String> graphs = indexConfigProvider.getSparqlService().executeSelectQuery(
      selectGraphQuery,
      resultSet -> Streams.stream(resultSet)
                          .map(querySolution -> querySolution.getResource("?graph"))
                          .map(Resource::getURI)
                          .filter(graphUri -> !StringUtils.endsWithIgnoreCase(graphUri, "/temp"))
                          .collect(Collectors.toList())
    );
    if(graphs.isEmpty()) {
      return StringUtils.EMPTY;
    }
    if(graphs.size() > 1) {
      log.warn("Resource {} is found in multiple graphs", instanceUri);
    }
    return graphs.get(0);
  }

  public String findGraphResourceUri(String instanceUri) {
    String selectGraphQuery = "select distinct ?graph { " +
                              "  GRAPH ?graph { " +
                              "    <" + instanceUri + "> ?p ?o ." +
                              "    filter (?p != <http://www.w3.org/1999/02/22-rdf-syntax-ns#type>) " +
                              "  } " +
                              "} limit 1";

    String askSingleTypeGraphQuery = "ask { " +
                                     "  GRAPH ?graph { " +
                                     "    <" + instanceUri + "> a ?o . " +
                                     "  } " +
                                     "}";

    List<String> graphs = indexConfigProvider.getSparqlService().executeSelectQuery(
      selectGraphQuery,
      resultSet -> Streams.stream(resultSet)
                          .map(querySolution -> querySolution.getResource("?graph"))
                          .map(Resource::getURI)
                          .filter(graphUri -> !StringUtils.endsWithIgnoreCase(graphUri, "/temp"))
                          .collect(Collectors.toList())
    );

    if (graphs.isEmpty()) {
      if (indexConfigProvider.getSparqlService().executeAskQuery(askSingleTypeGraphQuery)) {
        log.error("Uri {} is provided inside a graph with only type triple", instanceUri);
      }
      return StringUtils.EMPTY;
    }
    if(graphs.size() > 1) {
      log.warn("Resource {} is found in multiple graphs", instanceUri);
    }
    return graphs.get(0);
  }
}
