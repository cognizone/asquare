package zone.cogni.asquare.cube.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import zone.cogni.asquare.cube.convertor.ModelToJsonConversion;
import zone.cogni.asquare.cube.index.swap.IndexSwapService;
import zone.cogni.asquare.cube.index.swap.IndexSwapState;
import zone.cogni.asquare.cube.monitoredpool.MonitoredPool;
import zone.cogni.asquare.cube.pagination.PaginatedQuery;
import zone.cogni.asquare.cube.spel.SpelService;
import zone.cogni.asquare.service.elasticsearch.info.ElasticsearchMetadata;
import zone.cogni.asquare.service.elasticsearch.info.ElasticsearchMetadataService;
import zone.cogni.asquare.service.elasticsearch.v7.Elasticsearch7Store;
import zone.cogni.asquare.triplestore.RdfStoreService;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static zone.cogni.asquare.cube.index.InternalIndexingServiceUtils.getCallableForUri;
import static zone.cogni.asquare.cube.index.InternalIndexingServiceUtils.getIndexFolder;
import static zone.cogni.asquare.cube.index.InternalIndexingServiceUtils.getIndexMethodForUri;
import static zone.cogni.asquare.cube.index.InternalIndexingServiceUtils.getPartitionUris;
import static zone.cogni.asquare.cube.index.InternalIndexingServiceUtils.getUrisFromQuery;
import static zone.cogni.asquare.cube.index.InternalIndexingServiceUtils.getValidPartitionNames;

public class AliasedCollectionIndexingService
        extends IndexingServiceContext {

  private static final Logger log = LoggerFactory.getLogger(AliasedCollectionIndexingService.class);


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

  private final IndexingConfiguration indexingConfiguration;
  private final IndexSwapService indexSwapService;

  private final ElasticsearchMetadataService elasticsearchMetadataService;

  public AliasedCollectionIndexingService(@Nonnull SpelService spelService,
                                          @Nonnull PaginatedQuery paginatedQuery,
                                          @Nonnull MonitoredPool indexMonitoredPool,
                                          @Nonnull RdfStoreService rdfStore,
                                          @Nonnull Elasticsearch7Store elasticStore,
                                          @Nonnull ModelToJsonConversion modelToJsonConversion,
                                          @Nonnull Map<String, String> queryTemplateParameters,
                                          @Nonnull IndexingConfiguration indexingConfiguration,
                                          @Nonnull IndexSwapService indexSwapService) {
    this.spelService = spelService;
    this.paginatedQuery = paginatedQuery;
    this.indexMonitoredPool = indexMonitoredPool;
    this.rdfStore = rdfStore;
    this.elasticStore = elasticStore;
    this.modelToJsonConversion = modelToJsonConversion;
    this.queryTemplateParameters = queryTemplateParameters;
    this.indexingConfiguration = indexingConfiguration;
    this.indexSwapService = indexSwapService;
    this.elasticsearchMetadataService = new ElasticsearchMetadataService(new ElasticsearchMetadata.Configuration());
  }

  @Override
  public boolean isIndexRunning() {
    return indexMonitoredPool.isActive();
  }

  @Override
  @Nonnull
  public List<String> getIndexNames() {
    return indexingConfiguration.getValidIndexNames();
  }

  @Override
  public void indexAll(boolean clear) {
    if (clear) log.info("(indexAll) ignoring clear parameter");
    indexByName(getIndexNames(), clear);
  }

  @Override
  public void indexByName(@Nonnull List<String> indexes, boolean clear) {
    indexes.forEach(name -> indexByName(name, clear));
  }

  @Override
  public void indexByName(@Nonnull String index, boolean clear) {
    IndexingConfiguration.Index indexConfiguration = getIndexFolder(this, index);

    if (clear) log.info("(indexByName) no clear needed, will swap later");
    indexByPartition(index, getValidPartitionNames(indexConfiguration));
  }

  @Override
  @Nonnull
  public List<String> getCollectionNames(@Nonnull String index) {
    IndexingConfiguration.Index indexConfiguration = getIndexFolder(this, index);
    return getValidPartitionNames(indexConfiguration);
  }

  @Override
  public void indexByCollection(@Nonnull String index,
                                @Nonnull List<String> collections) {
    collections.forEach(collection -> indexByPartition(index, collection));
  }

  @Override
  public void indexByCollection(@Nonnull String index,
                                @Nonnull String collection) {
    IndexingConfiguration.Index indexConfiguration = getIndexFolder(this, index);
    indexByCollection(indexConfiguration, collection);
  }

  /**
   * @param indexConfiguration
   * @param collectionName     is index prefix name where all documents of a collection will be stored
   */
  private void indexByCollection(@Nonnull IndexingConfiguration.Index indexConfiguration,
                                 @Nonnull String collectionName) {
    log.info("(indexByCollection) index '{}' and collection: {}", indexConfiguration.getName(), collectionName);

    // get swap state information: input for indexing of collection
    String aliasName = indexConfiguration.getName();
    IndexSwapState indexSwapState = indexSwapService.getState(aliasName, collectionName);

    // log missing index
    if (indexSwapState.hasIndexMatchingAliasAndPrefix()) {
      log.info("(indexByCollection) no existing index found matching alias '{}' and prefix '{}'", aliasName, collectionName);
    }

    // create new index
    elasticStore.createIndex(indexSwapState.getNewIndexName(),
                             indexConfiguration.getSettingsJson());

    // get callables to run
    IndexingConfiguration.Partition partitionConfiguration = indexConfiguration.getValidPartition(collectionName);
    List<Callable<String>> callables = getCallables(indexSwapState.getNewIndexName(), partitionConfiguration);

    // run
    log.info("(indexByCollection) {} uris found", callables.size());
    indexMonitoredPool.invoke(callables);

    // delete old index
    deleteIndex(indexSwapState);
  }

  /**
   * @param indexToFill            name of index to fill
   * @param partitionConfiguration of object uris being loaded in index
   * @return stream of <code>Callable</code>s for a single collection in an index
   */
  @Nonnull
  private List<Callable<String>> getCallables(@Nonnull String indexToFill,
                                              @Nonnull IndexingConfiguration.Partition partitionConfiguration) {
    log.info("(getCallables) for index '{}' and collection '{}'", indexToFill, partitionConfiguration.getName());
    List<String> collectionConstructQueries = partitionConfiguration.getConstructQueries();
    List<Resource> facetQueryResources = partitionConfiguration.getFacetQueryResources();

    return getPartitionUris(this, partitionConfiguration)
            .stream()
            .map(uri -> getCallable(getIndexMethodForUri(this, indexToFill, facetQueryResources, uri),
                                    collectionConstructQueries,
                                    uri))
            .collect(Collectors.toList());
  }

  @Nonnull
  private Callable<String> getCallable(@Nonnull IndexMethod indexMethod,
                                       @Nonnull List<String> collectionConstructQueries,
                                       @Nonnull String uri) {
    return getCallableForUri(this, indexMethod, collectionConstructQueries, uri);
  }

  private void deleteIndex(@Nonnull IndexSwapState indexSwapState) {
    if (!indexSwapState.hasIndexMatchingAliasAndPrefix()) return;

    String oldIndexName = indexSwapState.getIndexMatchingAliasAndPrefix().getName();
    try {
      elasticStore.deleteIndex(oldIndexName);
    }
    catch (RuntimeException e) {
      // missing index?
      log.warn(".. delete index '{}' failed", oldIndexName, e);
      throw e;
    }
  }

  @Override
  public void indexUrisFromQuery(@Nonnull String index,
                                 @Nonnull String partition,
                                 @Nonnull String query) {
    log.info("(indexUrisFromQuery) started");

    List<String> uris = getUrisFromQuery(this, query);
    log.info("(indexUrisFromQuery) {} uris found", uris.size());

    indexUris(index, partition, uris);
    log.info("(indexUrisFromQuery) done");
  }

  @Override
  public void indexUris(@Nonnull String index,
                        @Nonnull String partition,
                        @Nonnull List<String> uris) {
    // TODO aliases cannot be used when indexing
    //
  }

  @Override
  public void ensureIndexExists(@Nonnull String index) {
    InternalIndexingServiceUtils.ensureIndexExists(this, index);
  }

  @Override
  protected SpelService getSpelService() {
    return spelService;
  }

  @Override
  protected PaginatedQuery getPaginatedQuery() {
    return paginatedQuery;
  }

  @Override
  protected IndexingConfiguration getIndexFolderService() {
    return indexingConfiguration;
  }

  @Override
  protected RdfStoreService getRdfStore() {
    return rdfStore;
  }

  @Override
  protected Elasticsearch7Store getElasticStore() {
    return elasticStore;
  }

  @Override
  protected ModelToJsonConversion getModelToJsonConversion() {
    return modelToJsonConversion;
  }

  @Override
  protected Map<String, String> getQueryTemplateParameters() {
    return queryTemplateParameters;
  }

  @Override
  protected ElasticsearchMetadataService getElasticsearchMetadataService() {
    return elasticsearchMetadataService;
  }

}
