package zone.cogni.asquare.cube.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vavr.control.Try;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.elasticsearch.index.IndexNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import zone.cogni.asquare.cube.convertor.ModelToJsonConversion;
import zone.cogni.asquare.cube.monitoredpool.MonitoredPool;
import zone.cogni.asquare.cube.pagination.PaginatedQuery;
import zone.cogni.asquare.cube.sparql2json.SparqlSelectToJson;
import zone.cogni.asquare.cube.spel.SpelService;
import zone.cogni.asquare.service.elasticsearch.v7.Elasticsearch7Store;
import zone.cogni.asquare.triplestore.RdfStoreService;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
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

  private static final String indent = "        ";

  private final ApplicationContext resourcePatternResolver;
  private final String configurationClasspath;

  private final SpelService spelService;
  private final PaginatedQuery paginatedQuery;
  private final MonitoredPool indexMonitoredPool;

  private final RdfStoreService rdfStore;
  private final Elasticsearch7Store elasticStore;
  private final ModelToJsonConversion modelToJsonConversion;

  private List<IndexMetadata> indexesMetadata;

  /**
   * Common parameters used in queries, like sparql endpoint urls etc...
   */
  private final Map<String, String> queryTemplateParameters;

  public ElasticsearchIndexingService(String configurationClasspath,
                                      Map<String, String> queryTemplateParameters,
                                      SpelService spelService,
                                      MonitoredPool indexMonitoredPool,
                                      Elasticsearch7Store elasticStore,
                                      PaginatedQuery paginatedQuery,
                                      RdfStoreService rdfStore,
                                      ModelToJsonConversion modelToJsonConversion,
                                      ApplicationContext resourcePatternResolver) {
    this.configurationClasspath = calculateConfigurationClasspath(configurationClasspath);
    this.queryTemplateParameters = queryTemplateParameters;
    this.spelService = spelService;
    this.indexMonitoredPool = indexMonitoredPool;
    this.elasticStore = elasticStore;
    this.paginatedQuery = paginatedQuery;
    this.rdfStore = rdfStore;
    this.modelToJsonConversion = modelToJsonConversion;
    this.resourcePatternResolver = resourcePatternResolver;
  }

  /**
   * Predictable version of classpath used to find all configuration data.
   *
   * @param configurationClasspath classpath
   * @return configurationClasspath starting with <code>classpath:</code> and not ending with <code>/</code>
   */
  @Nonnull
  private String calculateConfigurationClasspath(@Nonnull String configurationClasspath) {
    if (!configurationClasspath.startsWith("classpath:"))
      throw new RuntimeException("please make sure classpath is explicit by starting with classpath:");

    return configurationClasspath.endsWith("/") ? configurationClasspath.substring(0, configurationClasspath.length() - 1)
                                                : configurationClasspath;
  }

  private List<IndexMetadata> calculateIndexesMetadata() {
    Resource[] resources = getResources("/**/*");
    return getLocalPaths(resources).stream()
                                   .map(this::stripSlashAtFront)
                                   .filter(path -> path.contains("/")) // <- make sure it's a folder
                                   .map(path -> StringUtils.substringBefore(path, "/")) // <- take folder name
                                   .distinct()
                                   .sorted()
                                   .map(this::calculateIndexMetadata)
                                   .collect(Collectors.toList());
  }

  private IndexMetadata calculateIndexMetadata(String indexName) {
    IndexMetadata indexMetadata = new IndexMetadata();
    indexMetadata.setName(indexName);
    indexMetadata.setSettingsResource(calculateElasticSettingsResource(indexName));
    indexMetadata.setCollections(calculateCollectionsMetadata(indexMetadata));
    return indexMetadata;
  }

  private Resource calculateElasticSettingsResource(String indexName) {
    return getResource("/" + indexName + "/elastic-settings.json");
  }

  private List<CollectionMetadata> calculateCollectionsMetadata(IndexMetadata indexMetadata) {
    Resource[] resources = getResources("/" + indexMetadata.getName() + "/**/*");
    List<String> localPaths = getLocalPaths(resources);
    return localPaths.stream()
                     .map(this::stripSlashAtFront)
                     .map(path -> StringUtils.substringAfter(path, indexMetadata.getName() + "/")) // <- strip index
                     .filter(path -> path.contains("/")) // <- make sure it's a folder
                     .map(path -> StringUtils.substringBefore(path, "/")) // <- take folder name
                     .distinct()
                     .sorted()
                     .map(collectionName -> calculateCollectionMetadata(indexMetadata, collectionName))
                     .collect(Collectors.toList());
  }

  private CollectionMetadata calculateCollectionMetadata(IndexMetadata indexMetadata, String collection) {
    String index = indexMetadata.getName();

    CollectionMetadata result = new CollectionMetadata();
    result.setName(collection);
    result.setSelectQueryResources(getCollectionSelectQueries(index, collection));
    result.setConstructQueryResources(getCollectionConstructQueries(index, collection));
    result.setFacetQueryResources(getFacetQueries(index, collection));
    return result;
  }

  private List<Resource> getCollectionSelectQueries(String index, String collection) {
    return Arrays.asList(getResources("/" + index + "/" + collection + "/select-*.*"));
  }

  private List<Resource> getCollectionConstructQueries(String index, String collection) {
    return Arrays.asList(getResources("/" + index + "/" + collection + "/construct-*.*"));
  }

  @PostConstruct
  public void validate() {
    this.indexesMetadata = calculateIndexesMetadata();

    log.info("'{}' index service", configurationClasspath);

    List<String> indexNames = getIndexNames();
    log.info("{} index count: {}", indent, indexNames.size());

    if (indexNames.isEmpty()) {
      log.error("{} no valid indexes configured", indent);
    }

    if (!indexNames.isEmpty())
      log.info("{} valid indexes:   {}", indent, String.join(", ", indexNames));
    if (!getInvalidIndexNames().isEmpty())
      log.info("{} invalid indexes: {}", indent, String.join(", ", getInvalidIndexNames()));

    indexesMetadata.forEach(this::validateIndex);
  }

  private void validateIndex(IndexMetadata indexMetadata) {
    // valid or not
    if (indexMetadata.isValid())
      log.info("  '{}' index is valid", indexMetadata.getName());
    else
      log.error("  '{}' index NOT is valid", indexMetadata.getName());

    // settings
    if (!indexMetadata.isValidSettingsResource())
      log.error("{}   elastic-settings.json is missing", indent);

    // collections
    List<String> collectionNames = getValidCollectionNames(indexMetadata);
    log.info("{}   collection count: {}", indent, collectionNames.size());

    if (collectionNames.isEmpty()) {
      log.error("{}   no valid collections configured", indent);
    }

    if (!collectionNames.isEmpty())
      log.info("{}   valid collections:   {}", indent, String.join(", ", collectionNames));
    if (!getInvalidCollectionNames(indexMetadata).isEmpty())
      log.info("{}   invalid collections: {}", indent, String.join(", ", getInvalidCollectionNames(indexMetadata)));

    // deeper check into collections
    indexMetadata.getCollections().forEach(this::validateCollection);
  }

  private void validateCollection(CollectionMetadata collectionMetadata) {
    // valid or not
    if (collectionMetadata.isValid())
      log.info("    '{}' collection is valid", collectionMetadata.getName());
    else
      log.error("    '{}' collection NOT is valid", collectionMetadata.getName());

    // select
    if (collectionMetadata.getSelectQueryResources().isEmpty())
      log.error("{}     select-* queries are missing", indent);

    // construct
    if (collectionMetadata.getConstructQueryResources().isEmpty())
      log.error("{}     construct-* queries are missing", indent);

    // facet
    if (collectionMetadata.getFacetQueryResources().isEmpty())
      log.warn("{}     facets/* queries are missing", indent);
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
    return indexesMetadata.stream()
                          .filter(IndexMetadata::isValid)
                          .map(IndexMetadata::getName)
                          .collect(Collectors.toList());
  }

  private List<String> getInvalidIndexNames() {
    return indexesMetadata.stream()
                          .filter(indexMetadata -> !indexMetadata.isValid())
                          .map(IndexMetadata::getName)
                          .collect(Collectors.toList());
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
  private void indexByName(@Nonnull String index, boolean clear) {
    IndexMetadata indexMetadata = getIndexMetadata(index);

    clearIndex(indexMetadata, clear);
    indexByCollection(index, getValidCollectionNames(indexMetadata));
  }

  public List<String> getCollectionNames(String index) {
    return getValidCollectionNames(getIndexMetadata(index));
  }

  private IndexMetadata getIndexMetadata(String index) {
    return indexesMetadata.stream()
                          .filter(indexMetadata -> indexMetadata.getName().equals(index))
                          .findFirst()
                          .orElseThrow(() -> new RuntimeException("cannot find index with name '" + index + "'"));
  }

  private IndexMetadata getValidIndexMetadata(String index) {
    IndexMetadata indexMetadata = getIndexMetadata(index);
    return indexMetadata.isValid() ? indexMetadata
                                   : null;
  }

  private List<String> getValidCollectionNames(IndexMetadata indexMetadata) {
    return indexMetadata.getValidCollections()
                        .stream()
                        .map(CollectionMetadata::getName)
                        .collect(Collectors.toList());
  }

  public List<String> getInvalidCollectionNames(IndexMetadata indexMetadata) {
    return indexMetadata.getCollections()
                        .stream()
                        .filter(collectionMetadata -> !collectionMetadata.isValid())
                        .map(CollectionMetadata::getName)
                        .collect(Collectors.toList());
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


  private String stripSlashAtFront(String path) {
    return path.startsWith("/") ? StringUtils.substringAfter(path, "/") : path;
  }

  public void indexByCollection(String index, List<String> collections) {
    IndexMetadata indexMetadata = getIndexMetadata(index);
    indexByCollection(indexMetadata, collections);
  }

  private void indexByCollection(IndexMetadata indexMetadata, List<String> collections) {
    log.info("(indexByCollection) index '" + indexMetadata.getName() + "' and collections: " + String.join(", ", collections));
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
    log.info("(getCallables) for index '" + indexMetadata.getName() + "' and collection '" + collectionMetadata.getName() + "'");
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
   * @param uri                being indexing
   * @return <code>IndexMethod</code> instance
   */
  private IndexMethod getCollectionIndexMethod(IndexMetadata indexMetadata,
                                               CollectionMetadata collectionMetadata,
                                               String uri) {
    Resource[] queryResources = collectionMetadata.getFacetQueryResources().toArray(new Resource[0]);
    SparqlSelectToJson sparqlSelectToJson = new SparqlSelectToJson(queryResources,
                                                                   spelService,
                                                                   getCreateModelMap(uri));
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

  private List<Resource> getFacetQueries(@Nonnull String index, @Nonnull String collection) {
    return Arrays.asList(getResources("/" + index + "/" + collection + "/facets/*sparql*"));
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
      Map<String, String> createModelMap = getCreateModelMap(uri);

      return collectionConstructQueries
              .stream()
              .map(resource -> spelService.processTemplate(resource, createModelMap))
              .map(query -> paginatedQuery.getModel(rdfStore, query))
              .reduce(ModelFactory.createDefaultModel(), Model::add);
    };
  }

  private Map<String, String> getCreateModelMap(String uri) {
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

  private Resource getResource(String path) {
    return resourcePatternResolver.getResource(configurationClasspath + path);
  }

  private Resource[] getResources(String path) {
    String locationPattern = configurationClasspath + path;
    try {
      return resourcePatternResolver.getResources(locationPattern);
    }
    catch (FileNotFoundException e) {
      return new Resource[0];
    }
    catch (IOException e) {
      throw new RuntimeException("unable to resolve resources for '" + locationPattern + "'");
    }
  }

  /**
   * @param resources for which local paths will be returned
   * @return paths after <code>configurationClasspath</code> as <code>String</code>
   */
  private List<String> getLocalPaths(@Nonnull Resource[] resources) {
    String prefix = StringUtils.substringAfter(configurationClasspath, "classpath:");
    return Arrays.stream(resources)
                 .map(r -> getLocalPath(prefix, r))
                 .collect(Collectors.toList());
  }

  /**
   * @param prefix   path which is part of configuration and can be ignored
   * @param resource for which local path will be returned
   * @return path after <code>configurationClasspath</code> as <code>String</code>
   */
  @Nonnull
  private String getLocalPath(@Nonnull String prefix, @Nonnull Resource resource) {
    try {
      if (resource instanceof ClassPathResource) {
        String path = ((ClassPathResource) resource).getPath();
        return StringUtils.substringAfterLast(path, prefix);
      }
      else if (resource instanceof FileSystemResource) {
        String path = resource.getFile().getPath();
        return StringUtils.substringAfterLast(path, prefix);
      }
      else {
        throw new RuntimeException("local path not (yet) supported for resource of type " + resource.getClass()
                                                                                                    .getName());
      }
    }
    catch (IOException e) {
      throw new RuntimeException("problem getting path for resource of type " + resource.getClass().getName()
                                 + " and name " + resource.getFilename(), e);
    }
  }
}