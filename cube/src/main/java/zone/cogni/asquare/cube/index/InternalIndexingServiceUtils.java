package zone.cogni.asquare.cube.index;

import com.google.common.collect.Lists;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import zone.cogni.asquare.cube.convertor.ModelToJsonConversion;
import zone.cogni.asquare.cube.pagination.PaginatedQuery;
import zone.cogni.asquare.cube.sparql2json.SparqlSelectToJson;
import zone.cogni.asquare.cube.spel.SpelService;
import zone.cogni.asquare.service.elasticsearch.info.ElasticsearchMetadata;
import zone.cogni.asquare.service.elasticsearch.info.ElasticsearchMetadataService;
import zone.cogni.asquare.service.elasticsearch.v7.Elasticsearch7Store;
import zone.cogni.asquare.triplestore.RdfStoreService;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class InternalIndexingServiceUtils {

  private static final Logger log = LoggerFactory.getLogger(InternalIndexingServiceUtils.class);

  @Nonnull
  static List<String> getValidPartitionNames(@Nonnull IndexingConfiguration.Index indexConfiguration) {
    return indexConfiguration.getValidPartitions()
                             .stream()
                             .map(IndexingConfiguration.Partition::getName)
                             .collect(Collectors.toList());
  }

  @Nonnull
  static IndexingConfiguration.Index getIndexFolder(@Nonnull IndexingConfiguration indexingConfiguration,
                                                    @Nonnull String index) {
    return indexingConfiguration
            .getIndexConfigurations()
            .stream()
            .filter(indexFolder -> indexFolder.getName().equals(index))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("cannot find index with name '" + index + "'"));
  }

  @Nonnull
  static List<String> getPartitionUris(@Nonnull SpelService spelService,
                                       @Nonnull PaginatedQuery paginatedQuery,
                                       @Nonnull RdfStoreService rdfStore,
                                       @Nonnull Map<String, String> queryTemplateParameters,
                                       @Nonnull IndexingConfiguration.Partition partitionConfiguration) {

    return partitionConfiguration
            .getSelectQueries()
            .stream()
            .map(query -> spelService.processTemplate(query, queryTemplateParameters))
            .map(query -> paginatedQuery.select(rdfStore, query))
            .flatMap(queryResult -> paginatedQuery.convertSingleColumnUriToStringList(queryResult).stream())
            .collect(Collectors.toList());
  }

  static void indexSynchronously(@Nonnull SpelService spelService,
                                 @Nonnull PaginatedQuery paginatedQuery,
                                 @Nonnull RdfStoreService rdfStore,
                                 @Nonnull Elasticsearch7Store elasticStore,
                                 @Nonnull ModelToJsonConversion modelToJsonConversion,
                                 @Nonnull Map<String, String> queryTemplateParameters,
                                 @Nonnull IndexingConfiguration.Partition partitionConfiguration,
                                 @Nonnull String indexToFill,
                                 @Nonnull List<String> uris) {
    List<String> constructQueryResources = partitionConfiguration.getConstructQueries();
    List<Resource> facetQueryResources = partitionConfiguration.getFacetQueryResources();
    for (String uri : uris) {
      IndexMethod indexMethod = getIndexMethodForUri(spelService, paginatedQuery, rdfStore,
                                                     elasticStore, modelToJsonConversion, queryTemplateParameters,
                                                     indexToFill, facetQueryResources, uri);
      Supplier<Model> modelSupplier = getModelSupplier(spelService, paginatedQuery, rdfStore, queryTemplateParameters,
                                                       constructQueryResources, uri);
      indexMethod.indexOne(modelSupplier, uri, IndexMethod.Configuration.SyncElasticsearch);
    }
  }

  /**
   * Returns <code>IndexMethod</code> instance for selected <code>index</code> and <code>partition</code>
   *
   * @param spelService             to be used
   * @param paginatedQuery          to be used
   * @param rdfStore                to be used
   * @param elasticStore            to be used
   * @param modelToJsonConversion   to be used
   * @param queryTemplateParameters to be used
   * @param indexToFill             index being filled
   * @param facetQueryResources     of partition being indexed
   * @param uri                     of instance being indexed
   * @return <code>IndexMethod</code> instance
   */
  @Nonnull
  static IndexMethod getIndexMethodForUri(@Nonnull SpelService spelService,
                                          @Nonnull PaginatedQuery paginatedQuery,
                                          @Nonnull RdfStoreService rdfStore,
                                          @Nonnull Elasticsearch7Store elasticStore,
                                          @Nonnull ModelToJsonConversion modelToJsonConversion,
                                          @Nonnull Map<String, String> queryTemplateParameters,
                                          @Nonnull String indexToFill,
                                          @Nonnull List<Resource> facetQueryResources,
                                          @Nonnull String uri) {
    // TODO note use of a new instance of SparqlSelectToJson per URI is causing a huge memory usage
    Resource[] queryResources = facetQueryResources.toArray(new Resource[0]);
    SparqlSelectToJson sparqlSelectToJson = new SparqlSelectToJson(queryResources,
                                                                   spelService,
                                                                   getTemplateParameterMap(queryTemplateParameters, uri));
    return new IndexMethod(paginatedQuery,
                           rdfStore,
                           modelToJsonConversion,
                           indexToFill,
                           elasticStore,
                           sparqlSelectToJson);
  }

  /**
   * @param uri to be converted into an index callable
   * @return Callable instance based on uri
   */
  @Nonnull
  static Callable<String> getCallableForUri(@Nonnull SpelService spelService,
                                            @Nonnull PaginatedQuery paginatedQuery,
                                            @Nonnull RdfStoreService rdfStore,
                                            @Nonnull Map<String, String> queryTemplateParameters,
                                            @Nonnull IndexMethod indexMethod,
                                            @Nonnull List<String> partitionConstructQueries,
                                            @Nonnull String uri) {
    Supplier<Model> modelSupplier = getModelSupplier(spelService, paginatedQuery, rdfStore, queryTemplateParameters,
                                                     partitionConstructQueries, uri);
    return indexMethod.indexOneCallable(modelSupplier, uri);
  }

  @Nonnull
  static Supplier<Model> getModelSupplier(@Nonnull SpelService spelService,
                                          @Nonnull PaginatedQuery paginatedQuery,
                                          @Nonnull RdfStoreService rdfStore,
                                          @Nonnull Map<String, String> queryTemplateParameters,
                                          @Nonnull List<String> partitionConstructQueries,
                                          @Nonnull String uri) {
    return () -> {
      Map<String, String> createModelMap = getTemplateParameterMap(queryTemplateParameters, uri);

      return partitionConstructQueries
              .stream()
              .map(query -> spelService.processTemplate(query, createModelMap))
              .map(query -> paginatedQuery.getModel(rdfStore, query))
              .reduce(ModelFactory.createDefaultModel(), Model::add);
    };
  }

  @Nonnull
  static Map<String, String> getTemplateParameterMap(@Nonnull Map<String, String> queryTemplateParameters,
                                                     @Nonnull String uri) {
    Map<String, String> result = new HashMap<>(queryTemplateParameters);
    result.put("uri", uri);
    return result;
  }

  @Nonnull
  static List<String> getUrisFromQuery(@Nonnull PaginatedQuery paginatedQuery,
                                       @Nonnull RdfStoreService rdfStore,
                                       @Nonnull String query) {
    List<Map<String, RDFNode>> results = paginatedQuery.select(rdfStore, query);
    return paginatedQuery.convertSingleColumnUriToStringList(results);
  }

  /**
   * Indexable URIs are URIs which can be indexed.
   * In case URIs are passed which might not need to be indexed this method can filter them out.
   *
   * @param spelService             to be used
   * @param paginatedQuery          to be used
   * @param rdfStore                to be used
   * @param queryTemplateParameters to be used
   * @param partitionConfiguration  of partition (in index)
   * @param uris                    candidate uris which might or might not be indexed
   * @return list of uris which will be indexed
   */
  static List<String> getIndexableUris(@Nonnull SpelService spelService,
                                       @Nonnull PaginatedQuery paginatedQuery,
                                       @Nonnull RdfStoreService rdfStore,
                                       @Nonnull Map<String, String> queryTemplateParameters,
                                       IndexingConfiguration.Partition partitionConfiguration,
                                       List<String> uris) {
    if (!shouldCheckForIndexableUris(partitionConfiguration))
      return uris;


    List<List<String>> uriSubLists = Lists.partition(uris, 20);
    List<String> indexableUris =
            uriSubLists.stream()
                       .map(uriSubList -> selectIndexableUris(spelService, paginatedQuery, rdfStore, queryTemplateParameters, partitionConfiguration, uriSubList))
                       .flatMap(Collection::stream)
                       .collect(Collectors.toList());

    if (indexableUris.size() != uris.size()) {
      log.info("(index uri) ignoring {} of {} uris for indexing", uris.size() - indexableUris.size(), uris.size());
    }

    return indexableUris;
  }

  private static boolean shouldCheckForIndexableUris(@Nonnull IndexingConfiguration.Partition partitionConfiguration) {
    return partitionConfiguration.getSelectQueries()
                                 .stream()
                                 .anyMatch(query -> query.contains("#{[uriFilter]}"));
  }

  private static List<String> selectIndexableUris(@Nonnull SpelService spelService,
                                                  @Nonnull PaginatedQuery paginatedQuery,
                                                  @Nonnull RdfStoreService rdfStore,
                                                  @Nonnull Map<String, String> queryTemplateParameters,
                                                  @Nonnull IndexingConfiguration.Partition partitionConfiguration,
                                                  @Nonnull List<String> uris) {
    return partitionConfiguration.getSelectQueries()
                                 .stream()
                                 .map(query -> selectIndexableUris(spelService, paginatedQuery, rdfStore, queryTemplateParameters, query, uris))
                                 .flatMap(Collection::stream)
                                 .distinct()
                                 .collect(Collectors.toList());
  }

  private static List<String> selectIndexableUris(@Nonnull SpelService spelService,
                                                  @Nonnull PaginatedQuery paginatedQuery,
                                                  @Nonnull RdfStoreService rdfStore,
                                                  @Nonnull Map<String, String> queryTemplateParameters,
                                                  @Nonnull String templateQuery,
                                                  @Nonnull List<String> uris) {
    // add uriFilter parameter
    Map<String, String> templateParameterMap = new HashMap<>(queryTemplateParameters);
    templateParameterMap.put("uriFilter", getUriFilter(uris));

    // run query with filter
    String query = spelService.processTemplate(templateQuery, templateParameterMap);
    List<Map<String, RDFNode>> rows = paginatedQuery.select(rdfStore, query);
    return paginatedQuery.convertSingleColumnUriToStringList(rows);
  }

  private static String getUriFilter(List<String> uris) {
    String inClause = uris.stream()
                          .map(uri -> "<" + uri + ">")
                          .collect(Collectors.joining(","));
    return "\n\t filter ( ?uri in ( " + inClause + " ) )";
  }

  public static void ensureIndexExists(@Nonnull IndexingConfiguration indexingConfiguration,
                                       @Nonnull ElasticsearchMetadataService elasticsearchMetadataService,
                                       @Nonnull Elasticsearch7Store elasticStore,
                                       @Nonnull String index) {
    if (existsIndex(elasticsearchMetadataService, elasticStore, index)) return;

    IndexingConfiguration.Index indexConfiguration = getIndexFolder(indexingConfiguration, index);
    elasticStore.createIndex(index, indexConfiguration.getSettingsJson());
  }

  private static boolean existsIndex(@Nonnull ElasticsearchMetadataService elasticsearchMetadataService,
                                     @Nonnull Elasticsearch7Store elasticStore,
                                     @Nonnull String index) {
    ElasticsearchMetadata metadata = elasticsearchMetadataService
            .getElasticsearchMetadata(elasticStore);
    return metadata.getIndexes()
                   .stream()
                   .map(ElasticsearchMetadata.Index::getName)
                   .anyMatch(index::equals);
  }

  private InternalIndexingServiceUtils() {
  }

}
