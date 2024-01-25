package zone.cogni.asquare.virtuoso;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static zone.cogni.libs.sparqlservice.impl.HttpClientUtils.execute;

import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionBuilder;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTPBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.libs.sparqlservice.impl.HttpClientUtils;
import zone.cogni.libs.sparqlservice.impl.VirtuosoHelper;
import zone.cogni.sem.jena.template.JenaResultSetHandler;

public class VirtuosoRdfStoreService implements RdfStoreService {
  private static final Logger log = LoggerFactory.getLogger(VirtuosoRdfStoreService.class);

  protected final String rdfStoreUrl;
  protected final String rdfStoreUser;
  protected final String rdfStorePassword;
  private final boolean graphCrudUseBasicAuth;
  protected String modelContentType;

  private HttpClient httpClient;

  private QueryExecutionBuilder queryExecutionBuilder;

  public VirtuosoRdfStoreService(String rdfStoreUrl, String rdfStoreUser, String rdfStorePassword) {
    this(rdfStoreUrl, rdfStoreUser, rdfStorePassword, false);
  }

  public VirtuosoRdfStoreService(String rdfStoreUrl, String rdfStoreUser, String rdfStorePassword, boolean graphCrudUseBasicAuth) {
    this.rdfStoreUrl = rdfStoreUrl;
    this.rdfStoreUser = rdfStoreUser;
    this.rdfStorePassword = rdfStorePassword;
    this.graphCrudUseBasicAuth = graphCrudUseBasicAuth;
    //  TODO HttpClientBuilder httpClientBuilder = HttpClients.custom().useSystemProperties();
    httpClient = HttpClientUtils.createHttpClientBuilder(rdfStoreUser, rdfStorePassword).connectTimeout(
        Duration.ofMinutes(60)).build();
    queryExecutionBuilder = QueryExecutionHTTPBuilder.service(rdfStoreUrl).httpClient(buildHttpClient());
  }

  private HttpClient buildHttpClient() {
    return httpClientBuilder(true).build();
  }

  private HttpClient.Builder httpClientBuilder(final boolean withAuthentication) {
    return HttpClientUtils.createHttpClientBuilder(withAuthentication ? rdfStoreUser : null, rdfStorePassword).connectTimeout(Duration.ofSeconds(60));
  }

  @Override
  public void addData(Model model) {
    addData(model, null);
  }

  @Override
  public void addData(Model model, String graphUri) {
    addData(model, graphUri, false);
  }

  private void addData(Model model, String graphUri, boolean replace) {
    //If this method fails, check what is done in treaties (upload in batches with insert queries)
    StringWriter writer = new StringWriter();
    VirtuosoHelper.patchModel(model).write(writer, "ttl");
    byte[] data = writer.toString().getBytes(StandardCharsets.UTF_8);

    String url = StringUtils.substringBeforeLast(rdfStoreUrl, "/") + "/sparql-graph-crud-auth?" + // force Graph Update protocol
                 (StringUtils.isBlank(graphUri) ? "default" : ("graph-uri=" + graphUri));
    final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
        .header(CONTENT_TYPE, Lang.TURTLE.getHeaderString() + ";charset=utf-8");
    final BodyPublisher p = HttpRequest.BodyPublishers.ofByteArray(data);
    final HttpRequest request = (replace ? builder.PUT(p) : builder.POST(p)).build();
    HttpClientUtils.execute(request, httpClientBuilder(graphCrudUseBasicAuth).build());
  }

  @Override
  public <R> R executeSelectQuery(Query query, QuerySolutionMap bindings, JenaResultSetHandler<R> resultSetHandler,
                                  String context) {
    log.debug("Select [{}] - {} \n{}", context, bindings, query);

    query = buildQuery(query, bindings);

    try (QueryExecution queryExecution = queryExecutionBuilder.query(query).build()) {
      return resultSetHandler.handle(queryExecution.execSelect());
    }
  }

  @Override
  public boolean executeAskQuery(Query query, QuerySolutionMap bindings) {
    query = buildQuery(query, bindings);
    try (QueryExecution queryExecution = queryExecutionBuilder.query(query).build()) {
      return queryExecution.execAsk();
    }
  }

  @Override
  public Model executeConstructQuery(Query query, QuerySolutionMap bindings) {
    query = buildQuery(query, bindings);
    try (QueryExecution queryExecution = queryExecutionBuilder.query(query).build()) {
      // TODO if (modelContentType != null) { queryExecution.setModelContentType(modelContentType); }
      return queryExecution.execConstruct();
    }
  }

  @Override
  public void executeUpdateQuery(String updateQuery) {
    final HttpRequest request = HttpRequest
        .newBuilder(URI.create(rdfStoreUrl))
        .POST(BodyPublishers.ofString("query=" + updateQuery, StandardCharsets.UTF_8))
        .header(CONTENT_TYPE, Lang.TURTLE.getHeaderString())
        .build();
    execute(request, httpClient);
  }

  @Override
  public void delete() {
    throw new UnsupportedOperationException("unsupported operation");
  }

  protected Query buildQuery(Query query, QuerySolutionMap bindings) {
    ParameterizedSparqlString string = new ParameterizedSparqlString(query.toString(), bindings);
    return string.asQuery();
  }

  @Override
  public void replaceGraph(String graphUri, Model model) {
    addData(model, graphUri, true);
  }

}
