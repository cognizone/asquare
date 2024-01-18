package zone.cogni.libs.sparqlservice.impl;

import static com.google.common.net.HttpHeaders.CONTENT_LENGTH;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static zone.cogni.libs.sparqlservice.impl.Utils.checkOK;
import static zone.cogni.libs.sparqlservice.impl.Utils.execute;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.function.Function;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionBuilder;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTP;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTPBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.libs.sparqlservice.SparqlService;

public class VirtuosoSparqlService implements SparqlService {
  private static final Logger log = LoggerFactory.getLogger(VirtuosoSparqlService.class);

  private final boolean sparqlGraphCrudUseBasicAuth;

  private final QueryExecutionBuilder queryExecutionBuilder;

  private final Config config;

  public VirtuosoSparqlService(Config config) {
    this.config = config;
    queryExecutionBuilder = QueryExecutionHTTPBuilder.service(this.config.getUrl() + "/query").httpClient(buildHttpClient());
    sparqlGraphCrudUseBasicAuth = config.isGraphCrudUseBasicAuth();
  }

  private HttpClient buildHttpClient() {
    return httpClientBuilder(true).build();
  }

  private HttpClient.Builder httpClientBuilder(final boolean withAuthentication) {
    return Utils.createHttpClientBuilder(withAuthentication ? config.getUser() : null, config.getPassword()).connectTimeout(Duration.of(60, ChronoUnit.SECONDS));
  }

  @Deprecated
  private void init() {
    log.warn("Method VirtuosoSparqlService::init is no longer supported.");
  }

  @Override
  public void uploadTtlFile(File file) {
    int retry = 0;
    while (true) {
      try {
        String graphUri = StringUtils.removeEnd(file.getName(), ".ttl");
        String url = StringUtils.substringBeforeLast(config.getUrl(), "/") + "/sparql-graph-crud-auth?" + // force Graph Update protocol
            (StringUtils.isBlank(graphUri) ? "default" : ("graph-uri=" + graphUri));

        loadIntoGraph_exception(file, url);
        break;
      }
      catch (Exception e) {
        if (retry >= 3) throw new RuntimeException(e);
        retry++;
        log.error("Failed to upload, waiting 10s then doing retry", e);
        try {
          Thread.sleep(10000L);
        }
        catch (InterruptedException ignore) {
        }
      }
    }
  }

  private void loadIntoGraph_exception(byte[] data, String updateUrl, boolean replace)
      throws Exception {
    log.info("Run {} with basic auth: {}", updateUrl, sparqlGraphCrudUseBasicAuth);

    final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(updateUrl))
        .header(CONTENT_TYPE, Lang.TURTLE.getHeaderString() + ";charset=utf-8")
        .header(CONTENT_LENGTH, Integer.toString(data.length));
    final BodyPublisher p = HttpRequest.BodyPublishers.ofByteArray(data);
    final HttpRequest request = (replace ? builder.PUT(p) : builder.POST(p)).build();
    //  TODO  conn.setDoOutput(true);
    //  TODO  conn.setRequestProperty("charset", "utf-8");
    //  TODO  conn.setUseCaches(false);

    try {
      final HttpResponse<String> httpResponse = httpClientBuilder(sparqlGraphCrudUseBasicAuth)
          .followRedirects(Redirect.ALWAYS)
          .build().send(request, BodyHandlers.ofString());
      checkOK(httpResponse);
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private void loadIntoGraph_exception(File file, String updateUrl) throws Exception {
    byte[] data = FileUtils.readFileToByteArray(file);

    loadIntoGraph_exception(data, updateUrl, false);
  }

  @Override
  public Model queryForModel(String query) {
    try (QueryExecution queryExecution = QueryExecutionHTTP
        .service(config.getUrl())
        .query(query)
        .httpClient(buildHttpClient())
        .build()) {
      return queryExecution.execConstruct();
    }
    catch (Exception e) {
      log.error("get data failed", e);
      throw new RuntimeException(e);
    }
  }

  @Override
  public void executeUpdateQuery(String updateQuery) {
    final HttpClient httpClient = buildHttpClient();
    final HttpRequest request = HttpRequest
        .newBuilder(URI.create(config.getUrl()))
        .POST(HttpRequest.BodyPublishers.ofString("query=" + updateQuery))
        .build();
    execute(request, httpClient);
  }

  @Override
  public void upload(Model model, String graphUri) {
    upload(model, graphUri, false);
  }

  public void upload(Model model, String graphUri, boolean replace) {
    final StringWriter writer = new StringWriter();
    VirtuosoHelper.patchModel(model).write(writer, "ttl");
    try {
      final String url = StringUtils.substringBeforeLast(config.getUrl(), "/") + "/sparql-graph-crud-auth?" + // force Graph Update protocol
          (StringUtils.isBlank(graphUri) ? "default" : ("graph-uri=" + graphUri));
      loadIntoGraph_exception(writer.toString().getBytes(), url, replace);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public <R> R executeSelectQuery(String query, Function<ResultSet, R> resultHandler) {
    try (
        QueryExecution queryExecution = QueryExecutionHTTP
            .service(config.getUrl())
            .query(query)
            .httpClient(buildHttpClient())
            .build()) {
      return resultHandler.apply(queryExecution.execSelect());
    }
    catch (Exception ex) {
      log.error("Can not execute select query {}", query, ex);
      throw new RuntimeException(ex);
    }
  }

  @Override
  public boolean executeAskQuery(String askQuery) {
    try (
        QueryExecution queryExecution = QueryExecutionHTTP
            .service(config.getUrl())
            .query(askQuery)
            .httpClient(buildHttpClient())
            .build()) {
      return queryExecution.execAsk();
    }
    catch (Exception ex) {
      log.error("Can not execute ask query {}", askQuery, ex);
      throw new RuntimeException(ex);
    }
  }

  @Override
  public void dropGraph(String graphUri) {
    executeUpdateQuery("clear graph <" + graphUri + ">");
  }

  @Override
  public void replaceGraph(String graphUri, Model model) {
    upload(model, graphUri, true);
  }
}
