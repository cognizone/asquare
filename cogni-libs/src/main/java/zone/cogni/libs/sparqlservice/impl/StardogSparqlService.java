package zone.cogni.libs.sparqlservice.impl;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static zone.cogni.libs.sparqlservice.impl.HttpClientUtils.execute;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionBuilder;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTPBuilder;
import org.springframework.http.MediaType;
import zone.cogni.libs.jena.utils.JenaUtils;
import zone.cogni.libs.jena.utils.TripleSerializationFormat;
import zone.cogni.libs.sparqlservice.SparqlService;

public class StardogSparqlService implements SparqlService {
  private final String endpointUrl;
  private final HttpClient httpClient;
  private final QueryExecutionBuilder queryExecutionBuilder;

  public StardogSparqlService(Config config) {
    endpointUrl = config.getUrl();
    httpClient = HttpClientUtils.createHttpClientBuilder(config.getUser(), config.getPassword()).build();
    queryExecutionBuilder = QueryExecutionHTTPBuilder.service(endpointUrl + "/query").httpClient(httpClient);
  }

  @Override
  public void uploadTtlFile(File file) {
    final HttpRequest request;
    try {
      request = HttpRequest
          .newBuilder(URI.create(endpointUrl))
          .POST(HttpRequest.BodyPublishers.ofFile(file.toPath()))
          .header(CONTENT_TYPE, Lang.TURTLE.getHeaderString()+";charset=utf-8")
          .build();
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
    execute(request,httpClient);
  }

  @Override
  public Model queryForModel(String query) {
    try (QueryExecution queryExecution = queryExecutionBuilder.query(query).build()) {
      // jena adds empty defaultGraph param to URL because defaultGraph is null but is a "value", stardog doesn't like that
      // TODO check with empty default graph ((QueryEngineHTTP) queryExecution).setDefaultGraphURIs(Collections.emptyList());
      return queryExecution.execConstruct();
    }
  }

  @Override
  public void executeUpdateQuery(String updateQuery) {
    final HttpRequest request = HttpRequest
        .newBuilder(URI.create(endpointUrl + "/update"))
        .POST(HttpRequest.BodyPublishers.ofString("update=" + updateQuery, StandardCharsets.UTF_8))
        .header(CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        .build();
    execute(request, httpClient);
  }

  @Override
  public boolean executeAskQuery(String askQuery) {
    try (QueryExecution queryExecution = queryExecutionBuilder.query(askQuery).build()) {
      // jena adds empty defaultGraph param to URL because defaultGraph is null but is a "value", stardog doesn't like that
      // TODO check with empty default graph ((QueryEngineHTTP) queryExecution).setDefaultGraphURIs(Collections.emptyList());
      return queryExecution.execAsk();
    }
  }

  @Override
  public void upload(Model model, String graphUri) {
    updateGraph(graphUri, model);
  }

  @Override
  public void updateGraph(String graphUri, Model model) {
    upload(model, graphUri, false);
  }

  @Override
  public void replaceGraph(String graphUri, Model model) {
    upload(model, graphUri, true);
  }

  private void upload(Model model, String graphUri, boolean replace) {
    String graphStoreUrl = endpointUrl + "?graph=" + graphUri;
    byte[] body = JenaUtils.toByteArray(model, TripleSerializationFormat.turtle);
    final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(graphStoreUrl))
        .header(CONTENT_TYPE, Lang.TURTLE.getHeaderString() + ";charset=utf-8");
    final BodyPublisher p = HttpRequest.BodyPublishers.ofByteArray(body);
    final HttpRequest request = (replace ? builder.PUT(p) : builder.POST(p)).build();
    execute(request, httpClient);
  }

  @Override
  public <R> R executeSelectQuery(String query, Function<ResultSet, R> resultHandler) {
    try (QueryExecution queryExecution = queryExecutionBuilder.query(query).build()) {
      // jena adds empty defaultGraph param to URL because defaultGraph is null but is a "value", stardog doesn't like that
      // TODO check with empty default graph ((QueryEngineHTTP) queryExecution).setDefaultGraphURIs(Collections.emptyList());
      return resultHandler.apply(queryExecution.execSelect());
    }
  }

  @Override
  public void dropGraph(String graphUri) {
    executeUpdateQuery("drop silent graph <" + graphUri + ">");
  }
}
