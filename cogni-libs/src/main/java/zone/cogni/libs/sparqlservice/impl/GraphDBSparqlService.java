package zone.cogni.libs.sparqlservice.impl;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static zone.cogni.libs.core.utils.HttpClientUtils.execute;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import org.apache.commons.io.FileUtils;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionBuilder;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTPBuilder;
import org.springframework.http.MediaType;
import zone.cogni.libs.core.utils.HttpClientUtils;
import zone.cogni.libs.sparqlservice.SparqlService;

public class GraphDBSparqlService implements SparqlService {

  private final GraphDBConfig config;
  private final HttpClient httpClient;

  public GraphDBSparqlService(GraphDBConfig config) {
    this.config = config;
    //  TODO check loading from systemproperties - e.g. proxy settings?
    //  HttpClientBuilder httpClientBuilder = HttpClients.custom().useSystemProperties();
    httpClient = HttpClientUtils.createHttpClientBuilder(config.getUser(), config.getPassword()).build();
  }

  private QueryExecutionBuilder getQueryExecutionBuilder() {
    return QueryExecutionHTTPBuilder.service(config.getSparqlEndpoint()).httpClient(httpClient);
  }

  @Override
  public void uploadTtlFile(File file) {
    try {
      uploadTtl(file.toURI().toString(), FileUtils.readFileToString(file, "UTF-8"));
    }
    catch (IOException e) {
      throw new RuntimeException("Couldn't read file " + file.getName(), e);
    }
  }

  @Override
  public void upload(Model model, String graphUri) {
    StringWriter writer = new StringWriter();
    model.write(writer, "ttl");
    uploadTtl(graphUri, writer.toString());
  }

  private void uploadTtl(String graphUri, String ttl) {
    final HttpRequest request = HttpRequest
        .newBuilder()
        .POST(BodyPublishers.ofString(ttl, StandardCharsets.UTF_8))
        .header(CONTENT_TYPE, Lang.TURTLE.getHeaderString())
        .uri(URI.create(config.getSparqlUpdateEndpoint()+"?context=" + URLEncoder.encode("<"+graphUri+">", StandardCharsets.UTF_8)))
        .build();
    execute(request, httpClient);
  }

  @Override
  public Model queryForModel(String query) {
    try (QueryExecution queryExecution = getQueryExecutionBuilder().query(query).build()) {
      return queryExecution.execConstruct();
    }
  }

  @Override
  public void executeUpdateQuery(String updateQuery) {
    final HttpRequest request = HttpRequest
        .newBuilder(URI.create(config.getSparqlUpdateEndpoint()))
        .POST(HttpRequest.BodyPublishers.ofString("update=" + URLEncoder.encode(updateQuery, StandardCharsets.UTF_8), StandardCharsets.UTF_8))
        .header(CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        .build();
    execute(request, httpClient);
  }

  @Override
  public <R> R executeSelectQuery(String query, Function<ResultSet, R> resultHandler) {
    try (QueryExecution queryExecution = getQueryExecutionBuilder().query(query).build()) {
      return resultHandler.apply(queryExecution.execSelect());
    }
  }

  @Override
  public boolean executeAskQuery(String askQuery) {
    try (QueryExecution queryExecution = getQueryExecutionBuilder().query(askQuery).build()) {
      return queryExecution.execAsk();
    }
  }

  @Override
  public void dropGraph(String graphUri) {
    executeUpdateQuery("clear graph <" + graphUri + ">");
  }
}
