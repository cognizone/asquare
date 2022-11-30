package zone.cogni.asquare.cube.index;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.codec.binary.Hex;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.access.Params;
import zone.cogni.asquare.cube.convertor.ModelToJsonConversion;
import zone.cogni.asquare.cube.hash.ModelHasher;
import zone.cogni.asquare.cube.pagination.PaginatedQuery;
import zone.cogni.asquare.cube.sparql2json.SparqlSelectToJson;
import zone.cogni.asquare.cube.util.TimingUtil;
import zone.cogni.asquare.service.elasticsearch.v7.Elasticsearch7Store;
import zone.cogni.asquare.triplestore.RdfStoreService;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * <p>
 * Can index a single <code>URI</code> in three different ways
 * <ul>
 *   <li>delayed by returning a <code>Callable</code></li>
 *   <li>asynchronous</li>
 *   <li>synchronous</li>
 * </ul>
 * </p>
 *
 * <p>
 * For it too work it needs context
 * <ul>
 *   <li>an RDF store connection</li>
 *   <li>an Elasticsearch connection</li>
 *   <li>an Elasticsearch index</li>
 *   <li>a conversion profile from Jena Model to JSON</li>
 *   <li>a component to turn SPARQL into JSON for facets</li>
 * </ul>
 * </p>
 */
public class IndexMethod {

  public static class Configuration {

    public static final Configuration AsyncElasticsearch = new Configuration(Params.noRefresh());
    public static final Configuration SyncElasticsearch = new Configuration(Params.refresh());

    private Params params;

    public Configuration(Params params) {
      this.params = params;
    }

    public Params getParams() {
      return params;
    }
  }

  private static final Logger log = LoggerFactory.getLogger(IndexMethod.class);

  private final PaginatedQuery paginatedQuery;
  private final RdfStoreService rdfStoreService;
  private final ModelToJsonConversion modelToJsonConversion;
  private final Elasticsearch7Store elasticStore;
  private final String indexName;
  private final SparqlSelectToJson sparqlSelectToJson;

  public IndexMethod(ModelToJsonConversion modelToJsonConversion,
                     String indexName,
                     Elasticsearch7Store elasticStore) {
    this(null, null, modelToJsonConversion, indexName, elasticStore, null);
  }

  public IndexMethod(ModelToJsonConversion modelToJsonConversion,
                     String indexName,
                     Elasticsearch7Store elasticStore,
                     SparqlSelectToJson sparqlSelectToJson) {
    this(null, null, modelToJsonConversion, indexName, elasticStore, sparqlSelectToJson);
  }

  @Deprecated
  public IndexMethod(PaginatedQuery paginatedQuery,
                     RdfStoreService rdfStoreService,
                     ModelToJsonConversion modelToJsonConversion,
                     String indexName,
                     Elasticsearch7Store elasticStore) {
    this(paginatedQuery, rdfStoreService, modelToJsonConversion, indexName, elasticStore, null);
  }

  @Deprecated
  public IndexMethod(PaginatedQuery paginatedQuery,
                     RdfStoreService rdfStoreService,
                     ModelToJsonConversion modelToJsonConversion,
                     String indexName,
                     Elasticsearch7Store elasticStore,
                     SparqlSelectToJson sparqlSelectToJson) {
    this.paginatedQuery = paginatedQuery;
    this.rdfStoreService = rdfStoreService;
    this.modelToJsonConversion = modelToJsonConversion;
    this.indexName = indexName;
    this.elasticStore = elasticStore;
    this.sparqlSelectToJson = sparqlSelectToJson;
  }

  /**
   * Returns a <code>Callable</code> which can index
   * according to the <code>configuration</code>.
   *
   * @param graphUri uri of graph which contains data to be indexed
   * @param uri uri of root instance to be indexed
   * @return <code>Callable</code> which can asynchronously index
   */
  @Deprecated
  public Callable<String> indexOneCallable(String graphUri, String uri, Configuration configuration) {
    return () -> indexOne(graphUri, uri, configuration);
  }

  /**
   * Returns a <code>Callable</code> which can index
   * according to the <code>configuration</code>.
   *
   * @param modelSupplier supplier of the Model which contains data to be indexed
   * @param uri uri of root instance to be indexed
   * @return <code>Callable</code> which can asynchronously index
   */
  public Callable<String> indexOneCallable(Supplier<Model> modelSupplier, String uri, Configuration configuration) {
    return () -> indexOne(modelSupplier, uri, configuration);
  }

  /**
   * Returns a <code>Callable</code> which can asynchronously index.
   *
   * @param graphUri uri of graph which contains data to be indexed
   * @param uri uri of root instance to be indexed
   * @return <code>Callable</code> which can asynchronously index
   */
  @Deprecated
  public Callable<String> indexOneCallable(String graphUri, String uri) {
    return () -> indexOne(graphUri, uri, Configuration.AsyncElasticsearch);
  }

  /**
   * Returns a <code>Callable</code> which can asynchronously index.
   *
   * @param modelSupplier supplier of the Model which contains data to be indexed
   * @param uri uri of root instance to be indexed
   * @return <code>Callable</code> which can asynchronously index
   */
  public Callable<String> indexOneCallable(Supplier<Model> modelSupplier, String uri) {
    return () -> indexOne(modelSupplier, uri, Configuration.AsyncElasticsearch);
  }

  /**
   * Index <code>uri</code> in <code>graph</code>
   * according to the <code>configuration</code>.
   *
   * @param graphUri uri of graph which contains data to be indexed
   * @param uri uri of root instance to be indexed
   * @return "ok" on success
   */
  @Deprecated
  public String indexOne(String graphUri, String uri, Configuration configuration) {
    return indexOne(() -> getGraph(graphUri), uri, configuration);
  }

  @Deprecated
  public String indexOne(String graphUri, String uri) {
    return indexOne(() -> getGraph(graphUri), uri, Configuration.AsyncElasticsearch);
  }

  /**
   * Index <code>uri</code> in <code>graph</code>
   * according to the <code>configuration</code>.
   *
   * @param modelSupplier supplier of the Model which contains data to be indexed
   * @param uri uri of root instance to be indexed
   * @return "ok" on success
   */
  public String indexOne(Supplier<Model> modelSupplier, String uri, Configuration configuration) {
    long start = System.nanoTime();
    if (log.isDebugEnabled()) log.debug("indexing uri {} start", uri);

    ObjectNode document = convert(modelSupplier.get(), uri);
    saveDocument(uri, document, configuration.getParams());

    log.info("indexing uri {} done ({} ms)", uri, TimingUtil.millisSinceStart(start, 1));
    return "ok";
  }

  public ObjectNode convert(Model model, String uri) {
    ObjectNode objectNode = modelToJsonConversion.apply(model, uri);

    if (sparqlSelectToJson != null) addFacets(model, objectNode, uri);
    return objectNode;
  }

  private Model getGraph(String graphUri) {
    if (paginatedQuery == null || rdfStoreService == null) throw new RuntimeException("Not supported");
    return paginatedQuery.getGraph(rdfStoreService, graphUri);
  }

  private void saveDocument(String uri, ObjectNode document, Params params) {
    elasticStore.indexDocument(indexName, uri, document, params);
  }

  private ObjectNode addFacets(Model draftModel, ObjectNode objectNode, String uri) {
    long start = System.nanoTime();

    Map<String, RDFNode> bindings = ImmutableMap.of("uri", ResourceFactory.createResource(uri));

    ObjectNode facetNode = sparqlSelectToJson.convert(draftModel, bindings);
    facetNode.put("hash", getHash(draftModel));
    if (!facetNode.isEmpty()) {
      objectNode.set("facets", facetNode);
      log.info("(addFacets) took {} ms", TimingUtil.millisSinceStart(start, 1));
    }

    return objectNode;
  }

  private String getHash(Model model) {
    ModelHasher modelHasher = new ModelHasher();
    byte[] hash = modelHasher.apply(model);
    return Hex.encodeHexString(hash);
  }
}
