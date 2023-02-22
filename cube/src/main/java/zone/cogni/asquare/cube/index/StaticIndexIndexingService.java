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
public class StaticIndexIndexingService implements FolderBasedIndexingService {

  private static final Logger log = LoggerFactory.getLogger(StaticIndexIndexingService.class);

  private final int indexBlockSize;

  private final IndexingConfiguration indexingConfiguration;

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
                                    @Nonnull IndexingConfiguration indexingConfiguration,
                                    @Nonnull Map<String, String> queryTemplateParameters) {
    this(20000,
         spelService, paginatedQuery, indexMonitoredPool, rdfStore, elasticStore,
         modelToJsonConversion, indexingConfiguration, queryTemplateParameters);
  }

  public StaticIndexIndexingService(int indexBlockSize,
                                    @Nonnull SpelService spelService,
                                    @Nonnull PaginatedQuery paginatedQuery,
                                    @Nonnull MonitoredPool indexMonitoredPool,
                                    @Nonnull RdfStoreService rdfStore,
                                    @Nonnull Elasticsearch7Store elasticStore,
                                    @Nonnull ModelToJsonConversion modelToJsonConversion,
                                    @Nonnull IndexingConfiguration indexingConfiguration,
                                    @Nonnull Map<String, String> queryTemplateParameters) {
    this.indexBlockSize = indexBlockSize;
    this.indexingConfiguration = indexingConfiguration;
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
    return indexingConfiguration.getValidIndexNames();
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
    IndexingConfiguration.Index indexConfiguration = getIndexFolder(indexingConfiguration, index);

    clearIndex(indexConfiguration, clear);
    indexByPartition(index, getValidPartitionNames(indexConfiguration));
  }

  @Nonnull
  @Override
  public List<String> getCollectionNames(@Nonnull String index) {
    IndexingConfiguration.Index indexConfiguration = getIndexFolder(indexingConfiguration, index);
    return getValidPartitionNames(indexConfiguration);
  }

  /**
   * Deletes and recreates <code>index</code> if <code>clear</code> is <code>true</code>.
   *
   * @param indexConfiguration of index to be deleted and created
   * @param clear              if <code>false</code> this method does nothing
   */
  private void clearIndex(@Nonnull IndexingConfiguration.Index indexConfiguration, boolean clear) {
    if (!clear) return;

    log.info("(clearIndex) '{}' started", indexConfiguration.getName());

    deleteIndex(indexConfiguration);
    ensureIndexExists(indexConfiguration.getName());
    log.info("(clearIndex) '{}' done", indexConfiguration.getName());
  }

  /**
   * Deletes index in <code>elasticStore</code>.
   *
   * @param indexConfiguration of index
   */
  private void deleteIndex(@Nonnull IndexingConfiguration.Index indexConfiguration) {
    try {
      elasticStore.deleteIndex(indexConfiguration.getName());
    }
    catch (RuntimeException e) {
      // missing index?
      log.warn(".. delete index '{}' failed", indexConfiguration.getName(), e);
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
    IndexingConfiguration.Index indexConfiguration = getIndexFolder(indexingConfiguration, index);
    indexByCollection(indexConfiguration, collections);
  }

  private void indexByCollection(@Nonnull IndexingConfiguration.Index indexConfiguration,
                                 @Nonnull List<String> collections) {
    log.info("(indexByCollection) index '{}' and collections: {}", indexConfiguration.getName(), String.join(", ", collections));

    IndexFolderUriReport uriReport = loadUriReport(indexConfiguration, collections);
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
  private IndexFolderUriReport loadUriReport(@Nonnull IndexingConfiguration.Index indexConfiguration,
                                             @Nonnull List<String> collections) {
    IndexFolderUriReport result = new IndexFolderUriReport(indexConfiguration);

    collections.stream()
               .map(indexConfiguration::getValidPartition)
               .forEach(collectionFolder -> {
                 List<String> partitionUris = getPartitionUris(spelService, paginatedQuery, rdfStore,
                                                               queryTemplateParameters, collectionFolder);
                 result.addCollection(collectionFolder, partitionUris);
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
    IndexingConfiguration.Index indexConfiguration = collectionFolderUriReport.getIndexFolderUriReport()
                                                                              .getIndexFolder();
    IndexingConfiguration.Partition partitionConfiguration = collectionFolderUriReport.getCollectionFolder();

    log.info("(getCallables) for index '{}' and collection '{}'", indexConfiguration.getName(), partitionConfiguration.getName());

    List<String> collectionConstructQueries = partitionConfiguration.getConstructQueries();
    List<Resource> facetQueryResources = partitionConfiguration.getFacetQueryResources();
    return collectionFolderUriReport
            .getUris()
            .stream()
            .map(uri -> getCallable(getIndexMethod(indexConfiguration, facetQueryResources, uri),
                                    collectionConstructQueries,
                                    uri));
  }

  @Nonnull
  private IndexMethod getIndexMethod(@Nonnull IndexingConfiguration.Index indexConfiguration,
                                     @Nonnull List<Resource> facetQueryResources,
                                     @Nonnull String uri) {
    return getIndexMethodForUri(spelService, paginatedQuery, rdfStore, elasticStore, modelToJsonConversion, queryTemplateParameters,
                                indexConfiguration.getName(), facetQueryResources, uri);
  }

  @Nonnull
  private Callable<String> getCallable(@Nonnull IndexMethod indexMethod,
                                       @Nonnull List<String> collectionConstructQueries,
                                       @Nonnull String uri) {
    return getCallableForUri(spelService, paginatedQuery, rdfStore, queryTemplateParameters,
                             indexMethod, collectionConstructQueries, uri);
  }

  @Override
  public void indexUrisFromQuery(@Nonnull String index,
                                 @Nonnull String partition,
                                 @Nonnull String query) {
    log.info("(indexUrisFromQuery) started");

    List<String> uris = getUrisFromQuery(paginatedQuery, rdfStore, query);
    log.info("(indexUrisFromQuery) {} uris found", uris.size());

    indexUris(index, partition, uris);
    log.info("(indexUrisFromQuery) done");
  }

  @Override
  public void indexUris(@Nonnull String index,
                        @Nonnull String partition,
                        @Nonnull List<String> uris) {
    // get index and collection folder
    IndexingConfiguration.Index indexConfiguration = getIndexFolder(indexingConfiguration, index);
    IndexingConfiguration.Partition partitionConfiguration = indexConfiguration.getValidPartition(partition);

    List<String> indexableUris = getIndexableUris(spelService, paginatedQuery, rdfStore, queryTemplateParameters, partitionConfiguration, uris);

    if (indexableUris.size() > 10) {
      log.warn("Method should probably not be used when passing in too many uris." +
               " Elasticsearch is called synchronously which is slower than asynchronously." +
               " Number of URIs being indexed is {}.", indexableUris.size());
    }

    // index uris
    indexSynchronously(spelService, paginatedQuery, rdfStore, elasticStore, modelToJsonConversion, queryTemplateParameters,
                       partitionConfiguration, indexConfiguration.getName(), indexableUris);
  }

  @Override
  public void ensureIndexExists(@Nonnull String index) {
    InternalIndexingServiceUtils.ensureIndexExists(indexingConfiguration, elasticsearchMetadataService, elasticStore, index);
  }

}
