package zone.cogni.libs.sparqlservice.impl;

import io.vavr.control.Try;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.libs.sparqlservice.SparqlService;

import java.io.File;
import java.io.StringWriter;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class VirtuosoSparqlService implements SparqlService {
  private static final Logger log = LoggerFactory.getLogger(VirtuosoSparqlService.class);

  private final String endpointUrl;
  private final String endpointUser;
  private final String endpointPassword;
  private final boolean sparqlGraphCrudUseBasicAuth;

  public VirtuosoSparqlService(Config config) {
    endpointUrl = config.getUrl();
    endpointUser = config.getUser();
    endpointPassword = config.getPassword();
    sparqlGraphCrudUseBasicAuth = config.isGraphCrudUseBasicAuth();
  }

  protected CloseableHttpClient buildHttpClient() {
    HttpClientBuilder httpClientBuilder = HttpClients.custom().useSystemProperties();
    httpClientBuilder.setConnectionManager(new PoolingHttpClientConnectionManager(60L, TimeUnit.SECONDS));

    if (StringUtils.isNoneBlank(endpointUser, endpointPassword)) {
      CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
      credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(endpointUser, endpointPassword));
      httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
    }
    else {
      log.warn("Virtuoso executor service {} is configured without credentials.", endpointUrl);
    }
    return httpClientBuilder.build();
  }

  @Deprecated
  private void init() {
    log.warn("Method VirtuosoSparqlService::init is no longer supported.");
  }

  @Override
  public void uploadTtlFile(File file) {
    Authenticator.setDefault(new Authenticator() {
      protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(endpointUser, endpointPassword.toCharArray());
      }
    });

    int retry = 0;
    while (true) {
      try {
        String graphUri = StringUtils.removeEnd(file.getName(), ".ttl");
        String url = StringUtils.substringBeforeLast(this.endpointUrl, "/") + "/sparql-graph-crud-auth?" + // force Graph Update protocol
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

  private void loadIntoGraph_exception(byte[] data, String updateUrl, boolean replace) throws Exception {
    log.info("Run {} with basic auth: {}", updateUrl, sparqlGraphCrudUseBasicAuth);
    URL url = new URL(updateUrl);

    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setDoOutput(true);
    conn.setInstanceFollowRedirects(true);
    conn.setRequestMethod(replace ? "PUT" : "POST");
    conn.setRequestProperty("Content-Type", "application/x-turtle");
    conn.setRequestProperty("charset", "utf-8");
    conn.setRequestProperty("Content-Length", Integer.toString(data.length));
    if(sparqlGraphCrudUseBasicAuth) {
      conn.setRequestProperty("Authorization", "Basic " + Base64.encodeBase64String((endpointUser + ":" + endpointPassword).getBytes(StandardCharsets.UTF_8)));
    }
    conn.setUseCaches(false);
    conn.getOutputStream().write(data);

    int responseCode = conn.getResponseCode();
    Try<String> errorBody = Try.of(() -> IOUtils.toString(conn.getErrorStream(), StandardCharsets.UTF_8));

    conn.disconnect();

    if ((responseCode / 100) != 2) {
      errorBody.onFailure(ex -> log.error("Failed to read response body", ex))
               .onSuccess(log::error);

      throw new RuntimeException("Not 2xx as answer: " + conn.getResponseCode() + " " + conn.getResponseMessage());
    }
  }

  private void loadIntoGraph_exception(File file, String updateUrl) throws Exception {
    byte[] data = FileUtils.readFileToByteArray(file);

    loadIntoGraph_exception(data, updateUrl, false);
  }

  @Override
  public Model queryForModel(String query) {
    try (CloseableHttpClient httpClient = buildHttpClient();
         QueryExecution queryExecution = QueryExecutionFactory.sparqlService(endpointUrl, query, httpClient)) {
      return queryExecution.execConstruct();
    }
    catch (Exception e) {
      log.error("get data failed", e);
      throw new RuntimeException(e);
    }
  }

  @Override
  public void executeUpdateQuery(String updateQuery) {
    try (CloseableHttpClient httpClient = buildHttpClient()) {
      //HttpOp.setDefaultHttpClient(httpClient);
      HttpPost httpPost = new HttpPost(endpointUrl);
      httpPost.setEntity(new UrlEncodedFormEntity(Collections.singletonList(new BasicNameValuePair("query", updateQuery)), StandardCharsets.UTF_8));
      HttpResponse execute = httpClient.execute(httpPost);
      StatusLine statusLine = execute.getStatusLine();
      if (statusLine.getStatusCode() != 200) {
        try {
          String response = IOUtils.toString(execute.getEntity().getContent());
          log.error(response);
        }
        catch (Exception ignore) {
          log.error("failed to read response");
        }
        throw new RuntimeException("Update didn't answer 200 code: " + statusLine);
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void upload(Model model, String graphUri) {
    upload(model, graphUri, false);
  }

  public void upload(Model model, String graphUri, boolean replace) {
    Authenticator.setDefault(new Authenticator() {
      protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(endpointUser, endpointPassword.toCharArray());
      }
    });

    StringWriter writer = new StringWriter();
    model.write(writer, "ttl");
    try {
      String url = StringUtils.substringBeforeLast(this.endpointUrl, "/") + "/sparql-graph-crud-auth?" + // force Graph Update protocol
                   (StringUtils.isBlank(graphUri) ? "default" : ("graph-uri=" + graphUri));

      loadIntoGraph_exception(writer.toString().getBytes(), url, replace);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public <R> R executeSelectQuery(String query, Function<ResultSet, R> resultHandler) {
    try (CloseableHttpClient httpClient = buildHttpClient();
         QueryExecution queryExecution = QueryExecutionFactory.sparqlService(endpointUrl, query, httpClient)) {
      return resultHandler.apply(queryExecution.execSelect());
    }
    catch (Exception ex) {
      log.error("Can not execute select query {}", query, ex);
      throw new RuntimeException(ex);
    }
  }

  @Override
  public boolean executeAskQuery(String askQuery) {
    try (CloseableHttpClient httpClient = buildHttpClient();
         QueryExecution queryExecution = QueryExecutionFactory.sparqlService(endpointUrl, askQuery, httpClient)) {
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
