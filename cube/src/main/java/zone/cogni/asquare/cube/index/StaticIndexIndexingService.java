package zone.cogni.asquare.cube.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import zone.cogni.asquare.cube.convertor.ModelToJsonConversion;
import zone.cogni.asquare.cube.index.IndexFolderUriReport.CollectionFolderUriReport;
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
import static zone.cogni.asquare.cube.index.InternalIndexingServiceUtils.getIndexableUris;
import static zone.cogni.asquare.cube.index.InternalIndexingServiceUtils.getPartitionUris;
import static zone.cogni.asquare.cube.index.InternalIndexingServiceUtils.getUrisFromQuery;
import static zone.cogni.asquare.cube.index.InternalIndexingServiceUtils.getValidPartitionNames;
import static zone.cogni.asquare.cube.index.InternalIndexingServiceUtils.indexSynchronously;

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
public class StaticIndexIndexingService
        extends IndexingServiceContext {

  private static final Logger log = LoggerFactory.getLogger(StaticIndexIndexingService.class);

  private final int indexBlockSize;

  private final IndexFolderService indexFolderService;

  private final SpelService spelService;
  private final PaginatedQuery paginatedQuery;
  private final MonitoredPool indexMonitoredPool;

  private final RdfStoreService rdfStore;
  private final Elasticsearch7Store elasticStore;
  private final ModelToJsonConversion modelToJsonConversion;

  private final ElasticsearchMetadataService elasticsearchMetadataService;

  /**
   * Common parameters used in queries, like sparql endpoint urls etc...
   */
  private final Map<String, String> queryTemplateParameters;

  public StaticIndexIndexingService(@Nonnull SpelService spelService,
                                    @Nonnull PaginatedQuery paginatedQuery,
                                    @Nonnull MonitoredPool indexMonitoredPool,
                                    @Nonnull RdfStoreService rdfStore,
                                    @Nonnull Elasticsearch7Store elasticStore,
                                    @Nonnull ModelToJsonConversion modelToJsonConversion,
                                    @Nonnull IndexFolderService indexFolderService,
                                    @Nonnull Map<String, String> queryTemplateParameters) {
    this(20000,
         spelService, paginatedQuery, indexMonitoredPool, rdfStore, elasticStore,
         modelToJsonConversion, indexFolderService, queryTemplateParameters);
  }

  public StaticIndexIndexingService(int indexBlockSize,
                                    @Nonnull SpelService spelService,
                                    @Nonnull PaginatedQuery paginatedQuery,
                                    @Nonnull MonitoredPool indexMonitoredPool,
                                    @Nonnull RdfStoreService rdfStore,
                                    @Nonnull Elasticsearch7Store elasticStore,
                                    @Nonnull ModelToJsonConversion modelToJsonConversion,
                                    @Nonnull IndexFolderService indexFolderService,
                                    @Nonnull Map<String, String> queryTemplateParameters) {
    this.indexBlockSize = indexBlockSize;
    this.indexFolderService = indexFolderService;
    this.queryTemplateParameters = queryTemplateParameters;
    this.spelService = spelService;
    this.indexMonitoredPool = indexMonitoredPool;
    this.elasticStore = elasticStore;
    this.paginatedQuery = paginatedQuery;
    this.rdfStore = rdfStore;
    this.modelToJsonConversion = modelToJsonConversion;
    this.elasticsearchMetadataService = new ElasticsearchMetadataService(new ElasticsearchMetadata.Configuration());
  }

  @Override
  public boolean isIndexRunning() {
    return indexMonitoredPool.isActive();
  }

  @Override
  @Nonnull
  public List<String> getIndexNames() {
    return indexFolderService.getValidIndexNames();
  }

  @Override
  public void indexAll(boolean clear) {
    indexByName(getIndexNames(), clear);
  }

  @Override
  public void indexByName(@Nonnull List<String> indexes, boolean clear) {
    indexes.forEach(name -> indexByName(name, clear));
  }

  @Override
  public void indexByName(@Nonnull String index, boolean clear) {
    PartitionedIndexConfiguration partitionedIndexConfiguration = getIndexFolder(this, index);

    clearIndex(partitionedIndexConfiguration, clear);
    indexByPartition(index, getValidPartitionNames(partitionedIndexConfiguration));
  }

  @Nonnull
  @Override
  public List<String> getCollectionNames(@Nonnull String index) {
    PartitionedIndexConfiguration partitionedIndexConfiguration = getIndexFolder(this, index);
    return getValidPartitionNames(partitionedIndexConfiguration);
  }

  /**
   * Deletes and recreates <code>index</code> if <code>clear</code> is <code>true</code>.
   *
   * @param partitionedIndexConfiguration of index to be deleted and created
   * @param clear                         if <code>false</code> this method does nothing
   */
  private void clearIndex(@Nonnull PartitionedIndexConfiguration partitionedIndexConfiguration, boolean clear) {
    if (!clear) return;

    log.info("(clearIndex) '{}' started", partitionedIndexConfiguration.getName());

    deleteIndex(partitionedIndexConfiguration);
    ensureIndexExists(partitionedIndexConfiguration.getName());
    log.info("(clearIndex) '{}' done", partitionedIndexConfiguration.getName());
  }

  /**
   * Deletes index in <code>elasticStore</code>.
   *
   * @param partitionedIndexConfiguration of index
   */
  private void deleteIndex(@Nonnull PartitionedIndexConfiguration partitionedIndexConfiguration) {
    try {
      elasticStore.deleteIndex(partitionedIndexConfiguration.getName());
    }
    catch (RuntimeException e) {
      // missing index?
      log.warn(".. delete index '{}' failed", partitionedIndexConfiguration.getName(), e);
      throw e;
    }
  }

  @Override
  public void indexByCollection(@Nonnull String index,
                                @Nonnull String collection) {
    indexByPartition(index, Collections.singletonList(collection));
  }

  @Override
  public void indexByCollection(@Nonnull String index,
                                @Nonnull List<String> collections) {
    PartitionedIndexConfiguration partitionedIndexConfiguration = getIndexFolder(this, index);
    indexByCollection(partitionedIndexConfiguration, collections);
  }

  private void indexByCollection(@Nonnull PartitionedIndexConfiguration partitionedIndexConfiguration,
                                 @Nonnull List<String> collections) {
    log.info("(indexByCollection) index '{}' and collections: {}", partitionedIndexConfiguration.getName(), String.join(", ", collections));

    IndexFolderUriReport uriReport = loadUriReport(partitionedIndexConfiguration, collections);
    int originalUriReportSize = uriReport.getSize();
    log.info("(indexByCollection) loaded uri report, found {} uris", originalUriReportSize);

    while (!uriReport.isEmpty()) {
      IndexFolderUriReport subsetUriReport = uriReport.extractSubset(indexBlockSize);
      List<Callable<String>> callables = getCallables(subsetUriReport);

      log.info("(indexByCollection) processing subset of {} uris", callables.size());
      indexMonitoredPool.invoke(callables);

      log.info("(indexByCollection) processing subset done, {} uris remaining", uriReport.getSize());
    }

    log.info("(indexByCollection) done, processed {} uris", originalUriReportSize);
  }

  @Nonnull
  private IndexFolderUriReport loadUriReport(@Nonnull PartitionedIndexConfiguration partitionedIndexConfiguration,
                                             @Nonnull List<String> collections) {
    IndexFolderUriReport result = new IndexFolderUriReport(partitionedIndexConfiguration);

    collections.stream()
               .map(partitionedIndexConfiguration::getValidPartition)
               .forEach(collectionFolder -> {
                 result.addCollection(collectionFolder,
                                      getPartitionUris(this, collectionFolder));
               });

    return result;
  }

  /**
   * @param uriReport create callables for a single index folder
   * @return stream of <code>Callable</code>s for a single collection in an index
   */
  private List<Callable<String>> getCallables(IndexFolderUriReport uriReport) {
    return uriReport.getCollectionFolderReports()
                    .stream()
                    .flatMap(this::getCallables)
                    .collect(Collectors.toList());
  }

  /**
   * @param collectionFolderUriReport create callables for a single collection of uris
   * @return stream of <code>Callable</code>s for a single collection in an index
   */
  @Nonnull
  private Stream<Callable<String>> getCallables(@Nonnull CollectionFolderUriReport collectionFolderUriReport) {
    PartitionedIndexConfiguration partitionedIndexConfiguration = collectionFolderUriReport.getIndexFolderUriReport()
                                                                                           .getIndexFolder();
    PartitionConfiguration partitionConfiguration = collectionFolderUriReport.getCollectionFolder();

    log.info("(getCallables) for index '{}' and collection '{}'", partitionedIndexConfiguration.getName(), partitionConfiguration.getName());

    List<String> collectionConstructQueries = partitionConfiguration.getConstructQueries();
    List<Resource> facetQueryResources = partitionConfiguration.getFacetQueryResources();
    return collectionFolderUriReport
            .getUris()
            .stream()
            .map(uri -> getCallable(getIndexMethod(partitionedIndexConfiguration, facetQueryResources, uri),
                                    collectionConstructQueries,
                                    uri));
  }

  @Nonnull
  private IndexMethod getIndexMethod(@Nonnull PartitionedIndexConfiguration partitionedIndexConfiguration,
                                     @Nonnull List<Resource> facetQueryResources,
                                     @Nonnull String uri) {
    return getIndexMethodForUri(this, partitionedIndexConfiguration.getName(), facetQueryResources, uri);
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
    // get index and collection folder
    PartitionedIndexConfiguration partitionedIndexConfiguration = getIndexFolder(this, index);
    PartitionConfiguration partitionConfiguration = partitionedIndexConfiguration.getValidPartition(partition);

    List<String> indexableUris = getIndexableUris(this, partitionConfiguration, uris);

    if (indexableUris.size() > 10) {
      log.warn("Method should probably not be used when passing in too many uris." +
               " Elasticsearch is called synchronously which is slower than asynchronously." +
               " Number of URIs being indexed is {}.", indexableUris.size());
    }

    // index uris
    indexSynchronously(this, partitionConfiguration, partitionedIndexConfiguration.getName(), indexableUris);
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
  protected IndexFolderService getIndexFolderService() {
    return indexFolderService;
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
