package zone.cogni.libs.sparqlservice.impl;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import zone.cogni.libs.sparqlservice.SparqlService;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.function.Function;

public class FusekiSparqlService implements SparqlService {

  private final FusekiConfig config;
  private final Header authHeader;
  private final HttpClientContext authenticationHttpContext;

  @Deprecated
  public FusekiSparqlService(Config config) {
    this(FusekiConfig.from(config));
  }

  public FusekiSparqlService(FusekiConfig config) {
    this.config = config;
    if (StringUtils.isNoneBlank(config.getUser(), config.getPassword())) {
      String authEncoded = Base64.getEncoder().encodeToString((config.getUser() + ":" + config.getPassword()).getBytes(StandardCharsets.UTF_8));
      authHeader = new BasicHeader("Authorization", "Basic " + authEncoded);
      authenticationHttpContext = createAuthenticationHttpContext();
    }
    else {
      authHeader = null; //we're allowed to send null value to the "setHeader" method (whop whop)
      authenticationHttpContext = null;
    }
  }

  private HttpClientContext createAuthenticationHttpContext() {
    CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(config.getUser(), config.getPassword()));

    HttpClientContext context = HttpClientContext.create();
    context.setCredentialsProvider(credentialsProvider);
    context.setAuthCache(new BasicAuthCache()); //so after first call it will know it has to send the authentication
    return context;
  }

  @Override
  public void uploadTtlFile(File file) {
    try {
      String sparqlUrl = config.getGraphStoreUrl() + "?graph=" + StringUtils.removeEnd(file.getName(), ".ttl");

      Response response = Request.Post(sparqlUrl)
              .setHeader("Content-Type", config.getTurtleMimeType() + ";charset=utf-8")
              .setHeader(authHeader)
              .bodyFile(file, ContentType.create(config.getTurtleMimeType(), StandardCharsets.UTF_8))
              .execute();
      checkAndDiscardResponse(response);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Model queryForModel(String query) {
    //pass default graph, method without default graph doesn't use the HttpContext !!!
    try (QueryExecution queryExecution = QueryExecutionFactory.sparqlService(config.getQueryUrl(), query, null, null, authenticationHttpContext)) {
      return queryExecution.execConstruct();
    }
  }

  @Override
  public void executeUpdateQuery(String updateQuery) {
    try {
      Response response = Request
              .Post(config.getUpdateUrl())
              .setHeader(authHeader)
              .body(new UrlEncodedFormEntity(Collections.singletonList(new BasicNameValuePair("update", updateQuery)), StandardCharsets.UTF_8))
              .execute();
      checkAndDiscardResponse(response);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void upload(Model model, String graphUri) {
    String insertUrl = config.getGraphStoreUrl() + "?graph=" + graphUri;
    StringWriter writer = new StringWriter();
    try {
      model.write(writer, "ttl");
      Response response = Request.Post(insertUrl)
              .setHeader("Content-Type", config.getTurtleMimeType() + ";charset=utf-8")
              .setHeader(authHeader)
              .bodyByteArray(writer.toString().getBytes(), ContentType.create(config.getTurtleMimeType(), StandardCharsets.UTF_8))
              .execute();
      checkAndDiscardResponse(response);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void checkAndDiscardResponse(Response response) throws IOException {
    try {
      HttpResponse httpResponse = response.returnResponse();
      StatusLine statusLine = httpResponse.getStatusLine();
      if (statusLine.getStatusCode() / 100 == 2) return;
      throw new RuntimeException("Upload didn't answer 2xx code " + statusLine + ": " + getBody(httpResponse));
    }
    finally {
      response.discardContent();
    }
  }

  private String getBody(HttpResponse httpResponse) {
    try {
      return IOUtils.toString(httpResponse.getEntity().getContent(), StandardCharsets.UTF_8);
    }
    catch (IOException e) {
      return "Failed to get error body: " + e.getMessage();
    }
  }

  @Override
  public <R> R executeSelectQuery(String query, Function<ResultSet, R> resultHandler) {
    //pass default graph, method without default graph doesn't use the HttpContext !!!
    try (QueryExecution queryExecution = QueryExecutionFactory.sparqlService(config.getQueryUrl(), query, null, null, authenticationHttpContext)) {
      return resultHandler.apply(queryExecution.execSelect());
    }
  }

  @Override
  public boolean executeAskQuery(String askQuery) {
    //pass default graph, method without default graph doesn't use the HttpContext !!!
    try (QueryExecution queryExecution = QueryExecutionFactory.sparqlService(config.getQueryUrl(), askQuery, null, null, authenticationHttpContext)) {
      return queryExecution.execAsk();
    }
  }

  @Override
  public void dropGraph(String graphUri) {
    executeUpdateQuery("drop graph <" + graphUri + ">");
  }
}
