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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static zone.cogni.asquare.cube.index.InternalIndexingServiceUtils.getCallableForUri;
import static zone.cogni.asquare.cube.index.InternalIndexingServiceUtils.getIndexFolder;
import static zone.cogni.asquare.cube.index.InternalIndexingServiceUtils.getIndexMethodForUri;
import static zone.cogni.asquare.cube.index.InternalIndexingServiceUtils.getPartitionUris;
import static zone.cogni.asquare.cube.index.InternalIndexingServiceUtils.getUrisFromQuery;
import static zone.cogni.asquare.cube.index.InternalIndexingServiceUtils.getValidPartitionNames;
import static zone.cogni.asquare.cube.index.InternalIndexingServiceUtils.indexSynchronously;

public class AliasedIndexIndexingService
        extends IndexingServiceContext {

  private static final Logger log = LoggerFactory.getLogger(AliasedIndexIndexingService.class);

  private final IndexingConfiguration indexingConfiguration;

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

  private final IndexSwapService indexSwapService;

  private final ElasticsearchMetadataService elasticsearchMetadataService;

  public AliasedIndexIndexingService(@Nonnull IndexingConfiguration indexingConfiguration,
                                     @Nonnull SpelService spelService,
                                     @Nonnull PaginatedQuery paginatedQuery,
                                     @Nonnull MonitoredPool indexMonitoredPool,
                                     @Nonnull RdfStoreService rdfStore,
                                     @Nonnull Elasticsearch7Store elasticStore,
                                     @Nonnull ModelToJsonConversion modelToJsonConversion,
                                     @Nonnull Map<String, String> queryTemplateParameters,
                                     @Nonnull IndexSwapService indexSwapService) {
    this.indexingConfiguration = indexingConfiguration;
    this.spelService = spelService;
    this.paginatedQuery = paginatedQuery;
    this.indexMonitoredPool = indexMonitoredPool;
    this.rdfStore = rdfStore;
    this.elasticStore = elasticStore;
    this.modelToJsonConversion = modelToJsonConversion;
    this.queryTemplateParameters = queryTemplateParameters;
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
    if (clear) log.info("(indexByName) '{}' no clear needed, will swap later", index);

    IndexingConfiguration.Index indexConfiguration = getIndexFolder(this, index);

    // get swap state information
    String aliasName = indexConfiguration.getName();
    IndexSwapState indexSwapState = indexSwapService.getState(aliasName, aliasName);

    // log missing index
    if (!indexSwapState.hasIndexMatchingAliasAndPrefix()) {
      log.info("(indexByCollection) no existing index found matching alias '{}'", aliasName);
    }

    // create new index
    String indexToFill = indexSwapState.getNewIndexName();
    elasticStore.createIndex(indexToFill,
                             indexConfiguration.getSettingsJson());

    // TODO improve -> think about how to use state
    indexByCollection(indexToFill, indexConfiguration, getValidPartitionNames(indexConfiguration));

    // swap and delete old index
    indexSwapService.swap(indexSwapState);
    deleteIndex(indexSwapState);
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

  @Nonnull
  @Override
  public List<String> getCollectionNames(@Nonnull String index) {
    IndexingConfiguration.Index indexConfiguration = getIndexFolder(this, index);
    return getValidPartitionNames(indexConfiguration);
  }

  @Override
  public void indexByCollection(@Nonnull String index,
                                @Nonnull String collection) {
    indexByPartition(index, Collections.singletonList(collection));
  }

  @Override
  public void indexByCollection(@Nonnull String index,
                                @Nonnull List<String> collections) {
    IndexingConfiguration.Index indexConfiguration = getIndexFolder(this, index);

    ElasticsearchMetadata.Index actualIndex = indexSwapService.getIndexForAlias(indexConfiguration.getName());
    String indexToFill = actualIndex.getName();
    indexByCollection(indexToFill, indexConfiguration, collections);
  }

  private void indexByCollection(@Nonnull String indexToFill,
                                 @Nonnull IndexingConfiguration.Index indexConfiguration,
                                 @Nonnull List<String> collections) {
    log.info(
            "(indexByCollection) index '{}' (alias {}) and collections: {}",
            indexToFill, indexConfiguration.getName(), String.join(", ", collections)
    );
    List<Callable<String>> callables = getCallables(indexToFill, indexConfiguration, collections).collect(Collectors.toList());

    log.info("(indexByCollection) {} uris found", callables.size());
    indexMonitoredPool.invoke(callables);
  }

  /**
   * Returns a stream of <code>Callable</code>s for a set of collections in an index.
   *
   * @param indexToFill        index being filled, can be active index (in case of collection update) or a new index
   * @param indexConfiguration of index
   * @param collections        set of collections, each of them being a collection object urisgeing loaded in index
   * @return stream of <code>Callable</code>s for a set of collections in an index
   */
  @Nonnull
  private Stream<Callable<String>> getCallables(@Nonnull String indexToFill,
                                                @Nonnull IndexingConfiguration.Index indexConfiguration,
                                                @Nonnull List<String> collections) {
    return collections.stream()
                      .map(indexConfiguration::getValidPartition)
                      .flatMap(collectionFolder -> getCallables(indexToFill, indexConfiguration, collectionFolder));
  }

  /**
   * Returns stream of <code>Callable</code>s for a single collection in an index.
   *
   * @param indexToFill                   index being filled, can be active index (in case of collection update) or a new index
   * @param indexConfiguration for the knowing alias, which is identical to <code>name</code> (only used in logging)
   * @param partitionConfiguration        of object uris being loaded in index
   * @return stream of <code>Callable</code>s for a single collection in an index
   */
  @Nonnull
  private Stream<Callable<String>> getCallables(@Nonnull String indexToFill,
                                                @Nonnull IndexingConfiguration.Index indexConfiguration,
                                                @Nonnull IndexingConfiguration.Partition partitionConfiguration) {
    log.info(
            "(getCallables) for index '{}' (alias {}) and collection '{}'",
            indexToFill, indexConfiguration.getName(), partitionConfiguration.getName()
    );

    List<String> constructQueries = partitionConfiguration.getConstructQueries();
    List<Resource> facetQueries = partitionConfiguration.getFacetQueryResources();
    return getPartitionUris(this, partitionConfiguration)
            .stream()
            .map(uri -> getCallable(getIndexMethod(indexToFill, facetQueries, uri),
                                    constructQueries,
                                    uri));
  }

  @Nonnull
  private IndexMethod getIndexMethod(@Nonnull String indexToFill,
                                     @Nonnull List<Resource> facetQueryResources,
                                     @Nonnull String uri) {
    return getIndexMethodForUri(this, indexToFill, facetQueryResources, uri);
  }

  @Nonnull
  private Callable<String> getCallable(@Nonnull IndexMethod indexMethod,
                                       @Nonnull List<String> collectionConstructQueries,
                                       @Nonnull String uri) {
    return getCallableForUri(this, indexMethod, collectionConstructQueries, uri);
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
    if (uris.size() > 10) {
      log.warn("Method should probably not be used when passing in too many uris." +
               " Elasticsearch is called synchronously which is slower than asynchronously." +
               " Number of URIs being indexed is {}.", uris.size());
    }

    // get index configuration
    IndexingConfiguration.Index indexConfiguration = getIndexFolder(this, index);

    // get swap state information
    String aliasName = indexConfiguration.getName();

    // swap information
    // TODO this is not going to be fast enough... as a proof of concept it should work
    //  actual solution should be a "cache"
    IndexSwapState indexSwapState = indexSwapService.getState(aliasName, aliasName);

    // make sure swap is active
    if (indexSwapState.hasIndexMatchingAliasAndPrefix())
      throw new RuntimeException("no active index found for alias '" + indexSwapState.getAliasName() + "'");

    // do index
    IndexingConfiguration.Partition partitionConfiguration = indexConfiguration.getValidPartition(partition);
    String activeIndex = indexSwapState.getIndexMatchingAliasAndPrefix().getName();
    indexSynchronously(this, partitionConfiguration, activeIndex, uris);
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
