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

import static zone.cogni.asquare.cube.index.InternalIndexingServiceUtils.*;

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
    IndexFolder indexFolder = getIndexFolder(this, index);

    clearIndex(indexFolder, clear);
    indexByCollection(index, getValidCollectionFolderNames(indexFolder));
  }

  @Nonnull
  @Override
  public List<String> getCollectionNames(@Nonnull String index) {
    IndexFolder indexFolder = getIndexFolder(this, index);
    return getValidCollectionFolderNames(indexFolder);
  }

  /**
   * Deletes and recreates <code>index</code> if <code>clear</code> is <code>true</code>.
   *
   * @param indexFolder of index to be deleted and created
   * @param clear       if <code>false</code> this method does nothing
   */
  private void clearIndex(@Nonnull IndexFolder indexFolder, boolean clear) {
    if (!clear) return;

    log.info("(clearIndex) '{}' started", indexFolder.getName());

    deleteIndex(indexFolder);
    ensureIndexExists(indexFolder.getName());
    log.info("(clearIndex) '{}' done", indexFolder.getName());
  }

  /**
   * Deletes index in <code>elasticStore</code>.
   *
   * @param indexFolder of index
   */
  private void deleteIndex(@Nonnull IndexFolder indexFolder) {
    try {
      elasticStore.deleteIndex(indexFolder.getName());
    }
    catch (RuntimeException e) {
      // missing index?
      log.warn(".. delete index '{}' failed", indexFolder.getName(), e);
      throw e;
    }
  }

  @Override
  public void indexByCollection(@Nonnull String index,
                                @Nonnull String collection) {
    indexByCollection(index, Collections.singletonList(collection));
  }

  @Override
  public void indexByCollection(@Nonnull String index,
                                @Nonnull List<String> collections) {
    IndexFolder indexFolder = getIndexFolder(this, index);
    indexByCollection(indexFolder, collections);
  }

  private void indexByCollection(@Nonnull IndexFolder indexFolder,
                                 @Nonnull List<String> collections) {
    log.info("(indexByCollection) index '{}' and collections: {}", indexFolder.getName(), String.join(", ", collections));

    IndexFolderUriReport uriReport = loadUriReport(indexFolder, collections);
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
  private IndexFolderUriReport loadUriReport(@Nonnull IndexFolder indexFolder,
                                             @Nonnull List<String> collections) {
    IndexFolderUriReport result = new IndexFolderUriReport(indexFolder);

    collections.stream()
               .map(indexFolder::getValidCollectionFolder)
               .forEach(collectionFolder -> {
                 result.addCollection(collectionFolder,
                                      getCollectionUris(this, collectionFolder));
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
    IndexFolder indexFolder = collectionFolderUriReport.getIndexFolderUriReport().getIndexFolder();
    CollectionFolder collectionFolder = collectionFolderUriReport.getCollectionFolder();

    log.info("(getCallables) for index '{}' and collection '{}'", indexFolder.getName(), collectionFolder.getName());

    List<String> collectionConstructQueries = collectionFolder.getConstructQueries();
    List<Resource> facetQueryResources = collectionFolder.getFacetQueryResources();
    return collectionFolderUriReport
            .getUris()
            .stream()
            .map(uri -> getCallable(getIndexMethod(indexFolder, facetQueryResources, uri),
                                    collectionConstructQueries,
                                    uri));
  }

  @Nonnull
  private IndexMethod getIndexMethod(@Nonnull IndexFolder indexFolder,
                                     @Nonnull List<Resource> facetQueryResources,
                                     @Nonnull String uri) {
    return getIndexMethodForUri(this, indexFolder.getName(), facetQueryResources, uri);
  }

  @Nonnull
  private Callable<String> getCallable(@Nonnull IndexMethod indexMethod,
                                       @Nonnull List<String> collectionConstructQueries,
                                       @Nonnull String uri) {
    return getCallableForUri(this, indexMethod, collectionConstructQueries, uri);
  }

  @Override
  public void indexUrisFromQuery(@Nonnull String index,
                                 @Nonnull String collection,
                                 @Nonnull String query) {
    log.info("(indexUrisFromQuery) started");

    List<String> uris = getUrisFromQuery(this, query);
    log.info("(indexUrisFromQuery) {} uris found", uris.size());

    indexUris(index, collection, uris);
    log.info("(indexUrisFromQuery) done");
  }

  @Override
  public void indexUris(@Nonnull String index,
                        @Nonnull String collection,
                        @Nonnull List<String> uris) {
    // get index and collection folder
    IndexFolder indexFolder = getIndexFolder(this, index);
    CollectionFolder collectionFolder = indexFolder.getValidCollectionFolder(collection);

    List<String> indexableUris = getIndexableUris(this, collectionFolder, uris);

    if (indexableUris.size() > 10) {
      log.warn("Method should probably not be used when passing in too many uris." +
               " Elasticsearch is called synchronously which is slower than asynchronously." +
               " Number of URIs being indexed is {}.", indexableUris.size());
    }

    // index uris
    indexSynchronously(this, collectionFolder, indexFolder.getName(), indexableUris);
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