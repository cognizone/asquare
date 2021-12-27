package zone.cogni.asquare.cube.index;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.springframework.core.io.Resource;
import zone.cogni.asquare.cube.pagination.PaginatedQuery;
import zone.cogni.asquare.cube.sparql2json.SparqlSelectToJson;
import zone.cogni.asquare.cube.spel.SpelService;
import zone.cogni.asquare.triplestore.RdfStoreService;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class InternalIndexingServiceUtils {

  @Nonnull
  static List<String> getValidCollectionFolderNames(@Nonnull IndexFolder indexFolder) {
    return indexFolder.getValidCollectionFolders()
                      .stream()
                      .map(CollectionFolder::getName)
                      .collect(Collectors.toList());
  }

  @Nonnull
  static IndexFolder getIndexFolder(@Nonnull IndexingServiceContext context,
                                    @Nonnull String index) {
    return context.getIndexFolderService()
                  .getIndexFolders()
                  .stream()
                  .filter(indexFolder -> indexFolder.getName().equals(index))
                  .findFirst()
                  .orElseThrow(() -> new RuntimeException("cannot find index with name '" + index + "'"));
  }

  @Nonnull
  static List<String> getCollectionUris(@Nonnull IndexingServiceContext context,
                                        @Nonnull CollectionFolder collectionFolder) {
    SpelService spelService = context.getSpelService();
    PaginatedQuery paginatedQuery = context.getPaginatedQuery();
    RdfStoreService rdfStore = context.getRdfStore();
    Map<String, String> queryTemplateParameters = context.getQueryTemplateParameters();

    return collectionFolder.getSelectQueryResources()
                           .stream()
                           .map(resource -> spelService.processTemplate(resource, queryTemplateParameters))
                           .map(query -> paginatedQuery.select(rdfStore, query))
                           .flatMap(queryResult -> paginatedQuery.convertSingleColumnUriToStringList(queryResult)
                                                                   .stream())
                           .collect(Collectors.toList());
  }

  static void indexSynchronously(@Nonnull IndexingServiceContext indexingServiceContext,
                                 @Nonnull CollectionFolder collectionFolder,
                                 @Nonnull String indexToFill,
                                 @Nonnull List<String> uris) {
    List<Resource> constructQueryResources = collectionFolder.getConstructQueryResources();
    List<Resource> facetQueryResources = collectionFolder.getFacetQueryResources();
    for (String uri : uris) {
      IndexMethod indexMethod = getIndexMethodForUri(indexingServiceContext, indexToFill, facetQueryResources, uri);
      Supplier<Model> modelSupplier = getModelSupplier(indexingServiceContext, constructQueryResources, uri);
      indexMethod.indexOne(modelSupplier, uri, IndexMethod.Configuration.SyncElasticsearch);
    }
  }

  /**
   * Returns <code>IndexMethod</code> instance for selected <code>index</code> and <code>collection</code>
   *
   * @param context             service which needs an IndexMethod instance
   * @param indexToFill         index being filled
   * @param facetQueryResources of collection
   * @param uri                 of instance being indexed
   * @return <code>IndexMethod</code> instance
   */
  @Nonnull
  static IndexMethod getIndexMethodForUri(@Nonnull IndexingServiceContext context,
                                          @Nonnull String indexToFill,
                                          @Nonnull List<Resource> facetQueryResources,
                                          @Nonnull String uri) {
    Resource[] queryResources = facetQueryResources.toArray(new Resource[0]);
    SparqlSelectToJson sparqlSelectToJson = new SparqlSelectToJson(queryResources,
                                                                   context.getSpelService(),
                                                                   getTemplateParameterMap(context, uri));
    return new IndexMethod(context.getPaginatedQuery(),
                           context.getRdfStore(),
                           context.getModelToJsonConversion(),
                           indexToFill,
                           context.getElasticStore(),
                           sparqlSelectToJson);
  }

  /**
   * @param uri to be converted into an index callable
   * @return Callable instance based on uri
   */
  @Nonnull
  static Callable<String> getCallableForUri(@Nonnull IndexingServiceContext context,
                                            @Nonnull IndexMethod indexMethod,
                                            @Nonnull List<Resource> collectionConstructQueries,
                                            @Nonnull String uri) {
    Supplier<Model> modelSupplier = getModelSupplier(context, collectionConstructQueries, uri);
    return indexMethod.indexOneCallable(modelSupplier, uri);
  }

  @Nonnull
  static Supplier<Model> getModelSupplier(@Nonnull IndexingServiceContext context,
                                          @Nonnull List<Resource> collectionConstructQueries,
                                          @Nonnull String uri) {
    return () -> {
      Map<String, String> createModelMap = getTemplateParameterMap(context, uri);

      return collectionConstructQueries
              .stream()
              .map(resource -> context.getSpelService().processTemplate(resource, createModelMap))
              .map(query -> context.getPaginatedQuery().getModel(context.getRdfStore(), query))
              .reduce(ModelFactory.createDefaultModel(), Model::add);
    };
  }

  @Nonnull
  static Map<String, String> getTemplateParameterMap(@Nonnull IndexingServiceContext indexingServiceContext,
                                                     @Nonnull String uri) {
    Map<String, String> queryTemplateParameters = indexingServiceContext.getQueryTemplateParameters();
    Map<String, String> result = new HashMap<>(queryTemplateParameters);
    result.put("uri", uri);
    return result;
  }

  @Nonnull
  static List<String> getUrisFromQuery(@Nonnull IndexingServiceContext context,
                                       @Nonnull String query) {
    PaginatedQuery paginatedQuery = context.getPaginatedQuery();

    List<Map<String, RDFNode>> results = paginatedQuery.select(context.getRdfStore(), query);
    return paginatedQuery.convertSingleColumnUriToStringList(results);
  }

  private InternalIndexingServiceUtils() {
  }

}
