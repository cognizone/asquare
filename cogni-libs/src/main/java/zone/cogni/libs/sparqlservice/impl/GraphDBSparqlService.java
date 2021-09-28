package zone.cogni.libs.sparqlservice.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.libs.sparqlservice.SparqlService;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.function.Function;

public class GraphDBSparqlService implements SparqlService {
  private static final Logger log = LoggerFactory.getLogger(GraphDBSparqlService.class);

  private final GraphDBConfig config;
  private CloseableHttpClient httpClient;

  public GraphDBSparqlService(GraphDBConfig config) {
    this.config = config;
  }

  @PostConstruct
  void init() {
    HttpClientBuilder httpClientBuilder = HttpClients.custom()
                                                     .useSystemProperties();

    if (StringUtils.isNoneBlank(config.getUser(), config.getPassword())) {
      CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
      credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(config.getUser(), config.getPassword()));
      httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
    }
    else if (!StringUtils.isAllBlank(config.getUser(), config.getPassword())) {
      log.error("Endpoint credentials not properly configured");
    }
    this.httpClient = httpClientBuilder.build();
  }

  @Override
  public void uploadTtlFile(File file) {
    try {
      uploadTtl(file.getName(), file.toURI().toString(), FileUtils.readFileToString(file, "UTF-8"));
    }
    catch (IOException e) {
      throw new RuntimeException("Couldn't read file " + file.getName(), e);
    }
  }

  @Override
  public void upload(Model model, String graphUri) {
    StringWriter writer = new StringWriter();
    model.write(writer, "ttl");
    uploadTtl(graphUri, graphUri, writer.toString());
  }

  private void uploadTtl(String name, String graphUri, String ttl) {
    try {
      GraphBDUploadTtlBody body = new GraphBDUploadTtlBody()
        .setName(name)
        .setContext(graphUri)
        .setData(ttl);

      HttpPost request = new HttpPost(config.getImportTextEndpoint());
      StringEntity entity = new StringEntity(new ObjectMapper().writeValueAsString(body), ContentType.APPLICATION_JSON);
      request.setEntity(entity);

      executeHttpRequest(request, 202);
    }
    catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Model queryForModel(String query) {
    try (QueryExecution queryExecution = QueryExecutionFactory.sparqlService(config.getSparqlEndpoint(), query, httpClient)) {
      return queryExecution.execConstruct();
    }
  }

  @Override
  public void executeUpdateQuery(String updateQuery) {
    HttpPost request = new HttpPost(config.getSparqlUpdateEndpoint());
    request.setEntity(new UrlEncodedFormEntity(Collections.singletonList(new BasicNameValuePair("update", updateQuery)), StandardCharsets.UTF_8));

    executeHttpRequest(request, 204);
  }

  @Override
  public <R> R executeSelectQuery(String query, Function<ResultSet, R> resultHandler) {
    try (QueryExecution queryExecution = QueryExecutionFactory.sparqlService(config.getSparqlEndpoint(), query, httpClient)) {
      return resultHandler.apply(queryExecution.execSelect());
    }
  }

  @Override
  public boolean executeAskQuery(String askQuery) {
    try (QueryExecution queryExecution = QueryExecutionFactory.sparqlService(config.getSparqlEndpoint(), askQuery, httpClient)) {
      return queryExecution.execAsk();
    }
  }

  @Override
  public void dropGraph(String graphUri) {
    executeUpdateQuery("clear graph <" + graphUri + ">");
  }

  private void executeHttpRequest(HttpUriRequest request, int expectedResponseCode) {
    try (CloseableHttpResponse response = httpClient.execute(request)) {
      checkResponse(response, expectedResponseCode);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void checkResponse(HttpResponse response, int code) {
    StatusLine statusLine = response.getStatusLine();

    if (statusLine.getStatusCode() == code) return;
    String responseString = "Update didn't answer " + code + " code: " + statusLine + ". ";

    try {
      responseString += response.getEntity() == null ? "No response body from server." : EntityUtils.toString(response.getEntity());
    }
    catch (IOException e) {
      log.error("failed to read response", e);
    }

    throw new RuntimeException(responseString);
  }

}
