package zone.cogni.asquare.service.elasticsearch.v7;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Preconditions;
import io.vavr.control.Try;
import org.apache.jena.rdf.model.Resource;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import zone.cogni.asquare.access.AccessType;
import zone.cogni.asquare.access.ApplicationView;
import zone.cogni.asquare.access.ElasticAccessService;
import zone.cogni.asquare.service.elasticsearch.Params;
import zone.cogni.asquare.access.simplerdf.RdfResource;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.edit.DeltaResource;
import zone.cogni.asquare.rdf.RdfValue;
import zone.cogni.asquare.rdf.TypedResource;
import zone.cogni.asquare.service.jsonconversion.JsonConversionFactory;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.web.rest.controller.exceptions.NotFoundException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.vavr.API.$;
import static io.vavr.API.Case;
import static io.vavr.API.Match;
import static io.vavr.Predicates.instanceOf;

public class Elasticsearch7AccessService implements ElasticAccessService {

  private static final Logger log = LoggerFactory.getLogger(Elasticsearch7AccessService.class);

  private final String indexName;
  private final Elasticsearch7Store elasticStore;
  private final ApplicationProfile applicationProfile;
  private final JsonConversionFactory jsonConversion;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private Function<TypedResource, ObjectNode> facetConversion;

  private Params.Refresh refreshParam;

  public Elasticsearch7AccessService(String indexName,
                                     Elasticsearch7Store elasticStore,
                                     ApplicationProfile applicationProfile,
                                     JsonConversionFactory jsonConversion) {
    this.indexName = indexName;
    this.elasticStore = elasticStore;
    this.applicationProfile = applicationProfile;
    this.jsonConversion = jsonConversion;
  }

  public Params.Refresh getRefreshParam() {
    return refreshParam;
  }

  public void setRefreshParam(Params.Refresh refreshParam) {
    this.refreshParam = refreshParam;
  }

  public Function<TypedResource, ObjectNode> getFacetConversion() {
    return facetConversion;
  }

  public Elasticsearch7AccessService setFacetConversion(Function<TypedResource, ObjectNode> facetConversion) {
    this.facetConversion = facetConversion;
    return this;
  }

  @Override
  public AccessType getAccessType() {
    return AccessType.ELASTIC;
  }

  @Override
  public TypedResource getTypedResource(ApplicationProfile.Type type, Resource resource) {
    ObjectNode objectNode = getRawDocument(resource.getURI());

    if (objectNode == null) throw new NotFoundException(resource.getURI());

    List<? extends TypedResource> typedResources = jsonConversion.getJsonToUpdatableResource()
                                                                 .withApplicationView(new ApplicationView(this, applicationProfile))
                                                                 .withJsonRoot(objectNode)
                                                                 .get();

    Preconditions.checkState(typedResources.size() == 1);
    return typedResources.get(0);
  }

  @Override
  public List<? extends TypedResource> findAll(ApplicationProfile.Type type) {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(QueryBuilders.termQuery("data.type.keyword", type.getClassId()))
      .fetchSource(true);

    ObjectNode searchRequestBody = toObjectNode(searchSourceBuilder);
    ObjectNode searchResponseBody = elasticStore.search(indexName, searchRequestBody);

    return getTypedResourcesFrom(searchResponseBody);
  }

  private List<? extends TypedResource> getTypedResourcesFrom(ObjectNode searchResponseBody) {
    List<ObjectNode> objects = new ArrayList<>();

    ArrayNode hits = (ArrayNode) searchResponseBody.get("hits").get("hits");
    hits.forEach(hit -> {
      objects.add((ObjectNode) hit.get("_source").get("data"));
    });

    ArrayNode jsonNodes = objectMapper.createArrayNode().addAll(objects);
    ObjectNode jsonRoot = (ObjectNode) objectMapper.createObjectNode().set("data", jsonNodes);

    return jsonConversion.getJsonToUpdatableResource()
                         .withApplicationView(new ApplicationView(this, applicationProfile))
                         .withJsonRoot(jsonRoot)
                         .get();
  }


  private ObjectNode toObjectNode(SearchSourceBuilder searchSourceBuilder) {
    try {
      return (ObjectNode) objectMapper.readTree(searchSourceBuilder.toString());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public ObjectNode getRawDocument(String id) {
    return Try.of(() -> {
      ObjectNode response = (ObjectNode) elasticStore.getDocumentById(indexName, id).get("_source");
      if (response == null) throw new NotFoundException(id);
      return response;
    })
              .getOrElseGet(this::handleFail);
  }

  private ObjectNode handleFail(Throwable e) {
    return Match(e).of(
      Case($(instanceOf(HttpElasticsearch7Store.ElasticClientError.class)), () -> {
        if (((HttpElasticsearch7Store.ElasticClientError) e).getRawStatusCode() == 404) {
          throw new NotFoundException(e.getMessage());
        }
        throw (RuntimeException) e;
      }),
      Case($(instanceOf(RuntimeException.class)), () -> {
        throw (RuntimeException) e;
      }),
      Case($(), () -> {
        throw new IllegalStateException(e);
      })
    );
  }


  //todo functionality not related to this service
  @Override
  public RdfStoreService getRdfStoreService() {
    return null;
  }

  //todo functionality not related to this service, simply creates a RdfResource
  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Override
  public RdfResource getTypedResource() {
    return new RdfResource(this);
  }


  @Override
  public <T extends RdfValue> List<T> getValues(ApplicationProfile applicationProfile,
                                                TypedResource typedResource,
                                                ApplicationProfile.Attribute attribute) {
    return typedResource.getValues(attribute);
  }

  @Override
  public List<TypedResource> findAll(Supplier<ApplicationProfile.Type> typeSupplier,
                                     ApplicationView.AttributeMatcher... attributeMatchers) {
    throw new UnsupportedOperationException("Operation not supported yet");
  }

  @Override
  public void save(List<DeltaResource> deltaResources) {
    Params params = getParams();
    deltaResources.forEach(deltaResource -> {
      indexResource(deltaResource, params);
    });
  }

  public void indexResource(ApplicationView view, ApplicationProfile.Type type, Resource resource) {
    TypedResource typedResource = view.getRepository().getTypedResource(type, resource);
    indexResource(typedResource);
  }

  public void indexResource(TypedResource resource) {
    indexResource(resource, getParams());
  }

  public void indexResource(TypedResource resource, Params params) {
    ObjectNode facets = null;
    if (facetConversion != null) {
      facets = facetConversion.apply(resource);
    }
    indexResource(resource, facets, params);
  }

  public void indexResource(TypedResource resource, ObjectNode facets, Params params) {
    ObjectNode json = jsonConversion.getTypedResourceToJson().withTypedResource(resource).get();
    if (facets != null) {
      json.set("facets", facets);
    }
    if(params.hasGraph()) {
      json.set("graph", new TextNode(params.getGraph()));
    }

    indexJson(json, indexName, resource.getResource().getURI(), params);

    log.debug("indexed document '{}' for index '{}'.", resource.getResource().getURI(), indexName);
  }

  public void deleteAll(ApplicationProfile.Type type) {
    elasticStore.deleteByQuery(indexName, getTypeQuery(type));
  }

  private ObjectNode getTypeQuery(ApplicationProfile.Type type) {
    ObjectNode objectNode = objectMapper.createObjectNode();
    objectNode
      .putObject("query")
      .putObject("match")
      .put("data.type.keyword", type.getClassId());
    return objectNode;
  }

  public void deleteResource(TypedResource resource) {
    deleteResource(resource, getParams());
  }

  public void deleteResource(TypedResource resource, Params params) {
    elasticStore.deleteDocument(indexName, resource.getResource().getURI(), params);
  }

  public void reindex(ApplicationView sourceView, List<ApplicationProfile.Type> types, ObjectNode indexSettings) {
    log.info(".. loading data for index '{}' ...", indexName);

    List<? extends TypedResource> typedResources = types.stream()
                                                        .flatMap(type -> sourceView.getRepository().findAll(type).stream())
                                                        .collect(Collectors.toList());

    resetIndex(indexSettings);

    log.info(".. indexing '{}' ...", indexName);
    Runnable progress = getProgress(typedResources.size());

    typedResources.stream()
                  .peek(j -> progress.run())
                  .forEach(this::indexResource);
  }

  public Params getParams() {
    Params params = new Params();
    if (refreshParam != null) params.setRefresh(refreshParam);

    return params;
  }

  private void indexJson(ObjectNode jsonNode, String index, String id, Params params) {
    elasticStore.indexDocument(index, id, jsonNode, params);
  }

  public void resetIndex(ObjectNode indexSettings) {
    log.info(".. resetting index '{}' ...", indexName);
    try {
      elasticStore.deleteIndex(indexName);
      log.info(".. index '{}' deleted", indexName);
    }
    catch (RuntimeException e) {
      // missing index?
      log.warn(".. delete index '{}' failed", indexName, e);
      throw e;
    }
    elasticStore.createIndex(indexName, indexSettings);
    log.info(".. index '{}' created", indexName);
  }

  private Runnable getProgress(int size) {
    log.info(".. .. {}/{} ...", 0, size);
    AtomicInteger counter = new AtomicInteger(0);
    return () -> {
      if (counter.incrementAndGet() % 500 == 0) log.info(".. .. {}/{} ...", counter.get(), size);
    };
  }

  public String getIndexName() {
    return indexName;
  }

  public ObjectNode rawSearch(ObjectNode query) {
    return elasticStore.search(indexName, query);
  }

  public List<? extends TypedResource> search(ObjectNode query) {
    ObjectNode rawResults = this.rawSearch(query);
    ArrayNode hits = (ArrayNode) rawResults.get("hits").get("hits");
    List<TypedResource> results = new ArrayList<>();

    Function<JsonNode, List<? extends TypedResource>> conversionFunction = json ->
      jsonConversion.getJsonToUpdatableResource()
                    .withApplicationView(new ApplicationView(this, applicationProfile))
                    .withJsonRoot((ObjectNode) json)
                    .get();

    hits.forEach(hit -> results.addAll(conversionFunction.apply(hit.get("_source"))));
    return results;
  }
}
