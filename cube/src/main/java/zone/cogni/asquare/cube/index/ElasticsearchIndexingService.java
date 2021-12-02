package zone.cogni.asquare.cube.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vavr.control.Try;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.elasticsearch.index.IndexNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import zone.cogni.asquare.cube.convertor.ModelToJsonConversion;
import zone.cogni.asquare.cube.monitoredpool.MonitoredPool;
import zone.cogni.asquare.cube.pagination.PaginatedQuery;
import zone.cogni.asquare.cube.sparql2json.SparqlSelectToJson;
import zone.cogni.asquare.cube.spel.SpelService;
import zone.cogni.asquare.service.elasticsearch.v7.Elasticsearch7Store;
import zone.cogni.asquare.triplestore.RdfStoreService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Here is an expected folder structure for indexing:
 *
 * <pre>
 *   /rootFolder                <- root folder
 *     /name-1                  <- name of index
 *       elastic-settings.json
 *       /collection-1-1        <- name of collection
 *         select-*.sparql      <- select uris for collection
 *         construct-*.sparql   <- select triples for uri
 *         /facets              <- folder for facet queries
 *           select-1.sparql    <- facet query
 *           select-2.sparql
 *       /collection-1-2
 *         select-*.sparql
 *         ...
 *     /name-2
 *       elastic-settings.json
 *       /collection-2-1
 *         select-uris.sparql
 *         ...
 * </pre>
 */
public class ElasticsearchIndexingService {

  private static final Logger log = LoggerFactory.getLogger(ElasticsearchIndexingService.class);

  private final IndexMetadataService indexMetadataService;

  private final SpelService spelService;
  private final PaginatedQuery paginatedQuery;
  private final MonitoredPool indexMonitoredPool;

  private final RdfStoreService rdfStore;
  private final Elasticsearch7Store elasticStore;
  private final ModelToJsonConversion modelToJsonConversion;

  /**
   * Common parameters used in queries, like sparql endpoint urls etc...
   */
  private final Map<String, String> queryTemplateParameters;

  public ElasticsearchIndexingService(SpelService spelService,
                                      PaginatedQuery paginatedQuery,
                                      MonitoredPool indexMonitoredPool,
                                      RdfStoreService rdfStore,
                                      Elasticsearch7Store elasticStore,
                                      ModelToJsonConversion modelToJsonConversion,
                                      IndexMetadataService indexMetadataService,
                                      Map<String, String> queryTemplateParameters) {
    this.indexMetadataService = indexMetadataService;
    this.queryTemplateParameters = queryTemplateParameters;
    this.spelService = spelService;
    this.indexMonitoredPool = indexMonitoredPool;
    this.elasticStore = elasticStore;
    this.paginatedQuery = paginatedQuery;
    this.rdfStore = rdfStore;
    this.modelToJsonConversion = modelToJsonConversion;
  }

  /**
   * @return <code>true</code> if indexing is active
   */
  public boolean isIndexRunning() {
    return indexMonitoredPool.isActive();
  }

  /**
   * @return list of indexes managed by service for current <code>elasticStore</code>
   */
  @Nonnull
  public List<String> getIndexNames() {
    return indexMetadataService.getValidIndexNames();
  }

  /**
   * Fill all indexes.
   * Can do a clear index in <code>clear</code> parameter is set to <code>true</code>.
   *
   * @param clear clears index before filling it again
   */
  public void indexAll(boolean clear) {
    indexByName(getIndexNames(), clear);
  }

  /**
   * Fills listed indexes.
   * Can do a clear index in <code>clear</code> parameter is set to <code>true</code>.
   *
   * @param indexes list of indexes to be filled
   * @param clear   clears index before filling it again
   */
  public void indexByName(@Nonnull List<String> indexes, boolean clear) {
    indexes.forEach(name -> indexByName(name, clear));
  }

  /**
   * Fills a single index.
   * Can do a clear index in <code>clear</code> parameter is set to <code>true</code>.
   *
   * @param index to be filled
   * @param clear clears index before filling it again
   */
  public void indexByName(@Nonnull String index, boolean clear) {
    IndexMetadata indexMetadata = getIndexMetadata(index);

    clearIndex(indexMetadata, clear);
    indexByCollection(index, getValidCollectionNames(indexMetadata));
  }

  public List<String> getCollectionNames(String index) {
    return getValidCollectionNames(getIndexMetadata(index));
  }

  private List<String> getValidCollectionNames(IndexMetadata indexMetadata) {
    return indexMetadata.getValidCollections()
                        .stream()
                        .map(CollectionMetadata::getName)
                        .collect(Collectors.toList());
  }

  private IndexMetadata getIndexMetadata(String index) {
    return indexMetadataService.getIndexesMetadata()
                               .stream()
                               .filter(indexMetadata -> indexMetadata.getName().equals(index))
                               .findFirst()
                               .orElseThrow(() -> new RuntimeException("cannot find index with name '" + index + "'"));
  }

  /**
   * Deletes and recreates <code>index</code> if <code>clear</code> is <code>true</code>.
   *
   * @param indexMetadata of index to be deleted and created
   * @param clear         if <code>false</code> this method does nothing
   */
  private void clearIndex(@Nonnull IndexMetadata indexMetadata, boolean clear) {
    if (!clear) return;

    log.info("(clearIndex) '{}' started", indexMetadata.getName());

    deleteIndex(indexMetadata);
    createIndex(indexMetadata);
    log.info("(clearIndex) '{}' done", indexMetadata.getName());
  }

  /**
   * Deletes index in <code>elasticStore</code>.
   *
   * @param indexMetadata of index
   */
  private void deleteIndex(@Nonnull IndexMetadata indexMetadata) {
    try {
      elasticStore.deleteIndex(indexMetadata.getName());
    }
    catch (IndexNotFoundException ignore) {
      log.info("(deleteIndex) index '{}' did not exist.", indexMetadata.getName());
    }
  }

  /**
   * Creates index in <code>elasticStore</code>.
   * Also takes <code>elastic-settings.json</code> in <code>index</code> folder to configure the index.
   *
   * @param indexMetadata of index
   */
  private void createIndex(@Nonnull IndexMetadata indexMetadata) {
    elasticStore.createIndex(indexMetadata.getName(),
                             getElasticSettingsJson(indexMetadata));
  }

  /**
   * @param indexMetadata of index
   * @return <code>elastic-settings.json</code> in <code>index</code> folder as an <code>ObjectNode</code>
   */
  private ObjectNode getElasticSettingsJson(IndexMetadata indexMetadata) {
    Resource resource = indexMetadata.getSettingsResource();
    return (ObjectNode) Try.of(() -> new ObjectMapper().readTree(resource.getInputStream()))
                           .get();
  }

  public void indexByCollection(String index, List<String> collections) {
    IndexMetadata indexMetadata = getIndexMetadata(index);
    indexByCollection(indexMetadata, collections);
  }

  private void indexByCollection(IndexMetadata indexMetadata, List<String> collections) {
    log.info("(indexByCollection) index '{}' and collections: {}", indexMetadata.getName(), String.join(", ", collections));
    List<Callable<String>> callables = getCallables(indexMetadata, collections).collect(Collectors.toList());

    log.info("(indexByCollection) {} uris found", callables.size());
    this.indexMonitoredPool.invoke(callables);
  }

  /**
   * @param indexMetadata of index
   * @param collections   set of collections, each of them being a collection object urisgeing loaded in index
   * @return stream of <code>Callable</code>s for a set of collections in an index
   */
  private Stream<Callable<String>> getCallables(IndexMetadata indexMetadata, List<String> collections) {
    return collections.stream()
                      .map(indexMetadata::getValidCollection)
                      .flatMap(collectionMetadata -> getCallables(indexMetadata, collectionMetadata));
  }

  /**
   * @param indexMetadata      of index
   * @param collectionMetadata of object uris being loaded in index
   * @return stream of <code>Callable</code>s for a single collection in an index
   */
  private Stream<Callable<String>> getCallables(IndexMetadata indexMetadata, CollectionMetadata collectionMetadata) {
    log.info("(getCallables) for index '{}' and collection '{}'", indexMetadata.getName(), collectionMetadata.getName());
    List<Resource> collectionConstructQueries = collectionMetadata.getConstructQueryResources();
    return getCollectionUris(collectionMetadata)
            .stream()
            .map(uri -> getCallable(getCollectionIndexMethod(indexMetadata, collectionMetadata, uri),
                                    collectionConstructQueries,
                                    uri));
  }

  /**
   * Returns <code>IndexMethod</code> instance for selected <code>index</code> and <code>collection</code>
   *
   * @param indexMetadata      of index
   * @param collectionMetadata of collection
   * @param uri                of instance being indexed
   * @return <code>IndexMethod</code> instance
   */
  private IndexMethod getCollectionIndexMethod(IndexMetadata indexMetadata,
                                               CollectionMetadata collectionMetadata,
                                               String uri) {
    Resource[] queryResources = collectionMetadata.getFacetQueryResources().toArray(new Resource[0]);
    SparqlSelectToJson sparqlSelectToJson = new SparqlSelectToJson(queryResources,
                                                                   spelService,
                                                                   getTemplateParameterMap(uri));
    return new IndexMethod(paginatedQuery,
                           rdfStore,
                           modelToJsonConversion,
                           indexMetadata.getName(),
                           elasticStore,
                           sparqlSelectToJson);
  }

  private List<String> getCollectionUris(CollectionMetadata collectionMetadata) {
    return collectionMetadata.getSelectQueryResources()
                             .stream()
                             .map(resource -> spelService.processTemplate(resource, queryTemplateParameters))
                             .map(query -> paginatedQuery.select(rdfStore, query))
                             .flatMap(queryResult -> paginatedQuery.convertSingleColumnUriToStringList(queryResult)
                                                                   .stream())
                             .collect(Collectors.toList());
  }

  /**
   * @param uri to be converted into an index callable
   * @return Callable instance based on uri
   */
  private Callable<String> getCallable(IndexMethod indexMethod, List<Resource> collectionConstructQueries, String uri) {
    Supplier<Model> modelSupplier = getModelSupplier(collectionConstructQueries, uri);
    return indexMethod.indexOneCallable(modelSupplier, uri);
  }

  private Supplier<Model> getModelSupplier(List<Resource> collectionConstructQueries, String uri) {
    return () -> {
      Map<String, String> createModelMap = getTemplateParameterMap(uri);

      return collectionConstructQueries
              .stream()
              .map(resource -> spelService.processTemplate(resource, createModelMap))
              .map(query -> paginatedQuery.getModel(rdfStore, query))
              .reduce(ModelFactory.createDefaultModel(), Model::add);
    };
  }

  private Map<String, String> getTemplateParameterMap(String uri) {
    Map<String, String> result = new HashMap<>(queryTemplateParameters);
    result.put("uri", uri);
    return result;
  }

  /**
   * Currently not optimized for big lists of uris.
   * All uris are processed synchronously and one by one.
   *
   * @param index      being loaded
   * @param collection of object uris being loaded in index
   * @param query      to run, returns uris which much be indexed
   */
  public void indexUrisFromQuery(@Nonnull String index,
                                 @Nonnull String collection,
                                 @Nonnull String query) {
    log.info("(indexUrisFromQuery) started");

    List<String> uris = getUrisFromQuery(query);
    log.info("(indexUrisFromQuery) {} uris found", uris.size());

    indexUris(index, collection, uris);
    log.info("(indexUrisFromQuery) done");
  }

  private List<String> getUrisFromQuery(String query) {
    List<Map<String, RDFNode>> results = paginatedQuery.select(rdfStore, query);
    return paginatedQuery.convertSingleColumnUriToStringList(results);
  }

  /**
   * Currently not optimized for big lists of uris.
   * All uris are processed synchronously and one by one.
   *
   * @param index      being loaded
   * @param collection of object uris being loaded in index
   * @param uris       being indexed
   */
  public void indexUris(String index, String collection, List<String> uris) {
    if (uris.size() > 10) {
      log.warn("Method should probably not be used when passing in too many uris." +
               " Elasticsearch is called synchronously which is slower than asynchronously." +
               " Number of URIs being indexed is {}.", uris.size());
    }

    IndexMetadata indexMetadata = getValidIndexMetadata(index);
    if (indexMetadata == null) throw new RuntimeException("cannot index using an invalid index '" + index + "'");

    CollectionMetadata collectionMetadata = indexMetadata.getValidCollection(collection);
    List<Resource> constructQueryResources = collectionMetadata.getConstructQueryResources();
    for (String uri : uris) {
      IndexMethod indexMethod = getCollectionIndexMethod(indexMetadata, collectionMetadata, uri);
      Supplier<Model> modelSupplier = getModelSupplier(constructQueryResources, uri);
      indexMethod.indexOne(modelSupplier, uri, IndexMethod.Configuration.SyncElasticsearch);
    }
  }

  @Nullable
  private IndexMetadata getValidIndexMetadata(String index) {
    IndexMetadata indexMetadata = getIndexMetadata(index);
    return indexMetadata.isValid() ? indexMetadata : null;
  }

}