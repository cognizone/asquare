package zone.cogni.asquare.service.index;

import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import zone.cogni.asquare.access.ApplicationView;
import zone.cogni.asquare.service.elasticsearch.Params;
import zone.cogni.asquare.access.graph.GraphApplicationViewFactory;
import zone.cogni.asquare.access.graph.GraphViewService;
import zone.cogni.asquare.access.graph.SaveUtilities;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.rdf.TypedResource;
import zone.cogni.asquare.service.async.AsyncContext;
import zone.cogni.asquare.service.elasticsearch.ElasticStore;
import zone.cogni.asquare.service.jsonconversion.JsonConversionFactory;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.web.rest.controller.exceptions.NotFoundException;
import zone.cogni.libs.sparqlservice.SparqlService;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class GraphIndexService {
  private static final Logger log = LoggerFactory.getLogger(GraphIndexService.class);

  private final GraphApplicationViewFactory applicationViewFactory;
  private final IndexConfigProvider indexConfigProvider;
  private final Function<ResourceIndex, ApplicationProfile> applicationProfileSupplier;
  private final Function<ResourceIndex, Function<TypedResource, ObjectNode>> facetConversionSupplier;
  private final ElasticStore elasticsearchStore;
  private final JsonConversionFactory jsonConversion;

  public GraphIndexService(IndexConfigProvider indexConfigProvider,
                           GraphApplicationViewFactory applicationViewFactory,
                           JsonConversionFactory jsonConversion) {
    this.applicationViewFactory = applicationViewFactory;
    this.jsonConversion = jsonConversion;
    this.indexConfigProvider = indexConfigProvider;
    this.applicationProfileSupplier = indexConfigProvider.getApplicationProfileSupplier();
    this.elasticsearchStore = indexConfigProvider.getElasticStore();
    this.facetConversionSupplier = indexConfigProvider.getFacetConversionSupplier();
  }

  protected ApplicationView createApplicationView(ResourceIndex resourceIndex, RdfStoreService rdfStoreService, Model graphModel) {
    ApplicationProfile applicationProfile = applicationProfileSupplier.apply(resourceIndex);
    String graphUri = resourceIndex.getGraph();
    SparqlService sparqlService = new IndexConfigProvider.SparqlServiceImpl(rdfStoreService);
    if (StringUtils.isBlank(graphUri)) throw new IllegalStateException("Deprecation Alert: No graph URI provided for {} {}. Please check your query.");
    Model model = graphModel == null ? GraphViewService.loadPaginatedModel(sparqlService, graphUri) : graphModel;
    return applicationViewFactory.createGraphApplicationView(applicationProfile, model, graphUri, ImmutableList.of(SaveUtilities.persistGraphFunction(sparqlService)));
  }

  public Boolean indexUriSync(ResourceIndex resourceIndex, ApplicationView view) {
    return indexUriSync(resourceIndex, view, Params.waitFor());
  }

  public Boolean indexUriSync(ResourceIndex resourceIndex, ApplicationView view, Params params) {
    try {
      String localTypeName = IndexUtils.resolveTypeLocalNameByUri(resourceIndex.getType());
      ApplicationProfile.Type type = view.getApplicationProfile().getType(localTypeName);
      TypedResource resource = view.find(() -> type, resourceIndex.getUri());

      ObjectNode json = jsonConversion.getTypedResourceToJson().withTypedResource(resource).get();

      if (facetConversionSupplier != null) {
        Function<TypedResource, ObjectNode> facetConversion = facetConversionSupplier.apply(resourceIndex);
        ObjectNode facets = null;
        if (facetConversion != null) {
          facets = facetConversion.apply(resource);
        }
        if (facets != null) {
          json.set("facets", facets);
        }
      }

      if (params.hasGraph()) {
        json.set(IndexService.INDEX_GRAPH_NAME, new TextNode(params.getGraph()));
      }
      else if(StringUtils.isNotBlank(resourceIndex.getGraph())) {
        json.set(IndexService.INDEX_GRAPH_NAME, new TextNode(resourceIndex.getGraph()));
      }

      if(params.hasTimestamp()) {
        json.set(IndexService.INDEX_TIMESTAMP_MS_NAME, new LongNode(params.getTimestamp()));
      }

      elasticsearchStore.indexDocument(resourceIndex.getIndex(), resource.getResource().getURI(), json, params.filterParams());

      log.info("Indexing of {} finished", resourceIndex.getUri());
      return true;
    }
    catch (NotFoundException ex) {
      log.error("Indexing of {} failed because this resource was not found", resourceIndex.getUri());
    }
    catch (Exception ex) {
      log.error("Indexing of {} failed unexpectedly", resourceIndex.getUri(), ex);
    }
    return false;
  }

  @Deprecated
  public Boolean indexUriSync(ResourceIndex resourceIndex) {
    return indexUriSync(resourceIndex, indexConfigProvider.getRdfStoreService(), null);
  }

  public Boolean indexUriSync(ResourceIndex resourceIndex, RdfStoreService rdfStoreService) {
    return indexUriSync(resourceIndex, rdfStoreService, null);
  }

  public Boolean indexUriSync(ResourceIndex resourceIndex, RdfStoreService rdfStoreService, Model graphModel) {
    try {
      ApplicationView view = createApplicationView(resourceIndex, rdfStoreService, graphModel);
      return indexUriSync(resourceIndex, view);
    }
    catch (Exception ex) {
      log.error("Indexing of {} failed unexpectedly", resourceIndex.getUri(), ex);
    }
    return false;
  }

  @Deprecated
  @Async("indexingGraphTaskExecutor")
  public void indexGraphAsync(@AsyncContext("graph") String graph,
                              List<ResourceIndex> resources,
                              List<ResourceIndex> failedResources,
                              Params params) {
    indexGraphSync(graph, resources, failedResources, params, indexConfigProvider.getRdfStoreService(), null);
  }

  @Deprecated
  @Async("indexingGraphTaskExecutor")
  public void indexGraphAsync(@AsyncContext("graph") String graph,
                              List<ResourceIndex> resources,
                              List<ResourceIndex> failedResources) {
    indexGraphSync(graph, resources, failedResources);
  }

  @Async("indexingGraphTaskExecutor")
  public void indexGraphAsync(@AsyncContext("graph") String graph,
                              List<ResourceIndex> resources,
                              List<ResourceIndex> failedResources,
                              Params params, RdfStoreService rdfStoreService) {
    indexGraphSync(graph, resources, failedResources, params, rdfStoreService, null);
  }

  @Async("indexingGraphTaskExecutor")
  public void indexGraphAsync(@AsyncContext("graph") String graph,
                              List<ResourceIndex> resources,
                              List<ResourceIndex> failedResources,
                              Params params, RdfStoreService rdfStoreService, Model graphModel) {
    indexGraphSync(graph, resources, failedResources, params, rdfStoreService, graphModel);
  }

  @Async("indexingGraphTaskExecutor")
  public void indexGraphAsync(@AsyncContext("graph") String graph,
                              List<ResourceIndex> resources,
                              List<ResourceIndex> failedResources,
                              RdfStoreService rdfStoreService) {
    indexGraphSync(graph, resources, failedResources, rdfStoreService);
  }

  @Deprecated
  public void indexGraphSync(String graph, List<ResourceIndex> resources, List<ResourceIndex> failedResources) {
    indexGraphSync(graph, resources, failedResources, indexConfigProvider.getRdfStoreService());
  }

  public void indexGraphSync(String graph, List<ResourceIndex> resources, List<ResourceIndex> failedResources, RdfStoreService rdfStoreService) {
    indexGraphSync(graph, resources, failedResources, Params.waitFor(), rdfStoreService, null);
  }

  public void indexGraphSync(String graph, List<ResourceIndex> resources, List<ResourceIndex> failedResources, Params params, RdfStoreService rdfStoreService, Model graphModel) {

    Map<String, ApplicationView> viewCache = new HashMap<>();

    Iterator<ResourceIndex> iter = resources.iterator();
    while (iter.hasNext()) {
      ResourceIndex resourceIndex = iter.next();
      try {
        String viewCacheKey = resourceIndex.getIndex() + "-" + resourceIndex.getType();
        ApplicationView view = viewCache.computeIfAbsent(viewCacheKey, m -> createApplicationView(resourceIndex, rdfStoreService, graphModel));
        if (!indexUriSync(resourceIndex, view, params)) {
          log.info("Resource {} from graph {} indexing is failed. Adding to list of failed resources.", resourceIndex.getUri(), resourceIndex.getGraph());
          failedResources.add(resourceIndex);
        }
      }
      catch (Exception ex) {
        log.error("Indexing of resource {} from graph {} failed unexpectedly", resourceIndex.getUri(), graph, ex);
        failedResources.add(resourceIndex);
      }
    }
  }


}
