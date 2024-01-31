package zone.cogni.libs.core.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RIOT;
import org.apache.jena.riot.ResultSetMgr;
import org.apache.jena.riot.WebContent;
import org.apache.jena.riot.resultset.ResultSetLang;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility functions for working with the Apache HttpClient.
 */
public class ApacheHttpClientUtils {

  private final static Logger log = LoggerFactory.getLogger(ApacheHttpClientUtils.class);
  public static final String APPLICATION_SPARQL_RESULTS_XML = "application/sparql-results+xml";
  public static final String TEXT_TURTLE = "text/turtle";

  /**
   * Builds a http client given username and password for authentication. Apache HttpClient is
   * capable of delivering both Basic auth and Digest auth.
   *
   * @param username username to use for authentication
   * @param password password to use for authentication
   * @return http client
   */
  private static CloseableHttpClient buildHttpClient(final String username, final String password) {
    final HttpClientBuilder httpClientBuilder = HttpClients.custom().useSystemProperties();
    httpClientBuilder.setConnectionManager(
        new PoolingHttpClientConnectionManager(60L, TimeUnit.SECONDS));

    if (StringUtils.isNoneBlank(username, password)) {
      CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
      credentialsProvider.setCredentials(AuthScope.ANY,
          new UsernamePasswordCredentials(username, password));
      httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
    } else {
      log.warn("Service is configured without credentials.");
    }
    return httpClientBuilder.build();
  }

  /**
   * Ensures the response is 2xx. Throws a Runtime exception otherwise.
   *
   * @param response HTTP response to check.
   */
  private static void ensureResponseOK(HttpResponse response) {
    if ((response.getStatusLine().getStatusCode() / 100) != 2) {
      throw new RuntimeException(
          "Not 2xx as answer: " + response.getStatusLine().getStatusCode() + " "
              + response.getStatusLine().getReasonPhrase());
    }
  }

  /**
   * Strips charset postfix from the contentType.
   *
   * @param contentType to process.
   */
  private static String removeCharset(final String contentType) {
    if (contentType.contains(";")) {
      return contentType.substring(0, contentType.indexOf(';'));
    } else {
      return contentType;
    }
  }

  /**
   * Gets the language of the SPARQL query response .
   *
   * @param response     response to analyse content-type of.
   * @param acceptHeader the request accept header to be used as a fallback value.
   * @return ResultSet language (XML/JSON)
   */
  private static Lang getResultSetLanguage(final HttpResponse response, final String acceptHeader) {
    String actualContentType = response.getFirstHeader(HttpHeaders.CONTENT_TYPE).getValue();
    actualContentType = removeCharset(actualContentType);

    // If the server fails to return a Content-Type then we will assume
    // the server returned the type we asked for
    if (actualContentType.isEmpty()) {
      actualContentType = acceptHeader;
    }

    RIOT.init();
    Lang lang = RDFLanguages.contentTypeToLang(actualContentType);
    if (lang == null) {
      // Any specials :
      // application/xml for application/sparql-results+xml
      // application/json for application/sparql-results+json
      if (actualContentType.equals(WebContent.contentTypeXML)) {
        lang = ResultSetLang.RS_XML;
      } else if (actualContentType.equals(WebContent.contentTypeJSON)) {
        lang = ResultSetLang.RS_JSON;
      }
    }
    return lang;
  }

  private static HttpPost createPost(final String sparqlServiceUrl, final String acceptHeader,
      final String username, final String password, final boolean addBasicAuth) {
    final HttpPost httpPost = new HttpPost(sparqlServiceUrl);
    httpPost.setHeader(HttpHeaders.CONTENT_TYPE, "application/sparql-query");
    httpPost.setHeader(HttpHeaders.ACCEPT, acceptHeader);
    if (addBasicAuth) {
      httpPost.setHeader(HttpHeaders.AUTHORIZATION,
          "Basic " + Base64.encodeBase64String(
              (username + ":" + password).getBytes(
                  StandardCharsets.UTF_8)));
    }
    return httpPost;
  }

  /**
   * Executes and update request
   *
   * @param url          endpoint to reach
   * @param username     to authenticate with
   * @param password     to authenticate with
   * @param addBasicAuth whether the "Authorization Basic ..." header shall be added
   * @param httpEntity   payload
   * @param put          whether a put (true) or a post (false)
   * @param contentType  to send the data with
   */
  public static void executeAuthenticatedPostOrPut(
      final String url,
      final String username,
      final String password,
      final boolean addBasicAuth,
      final HttpEntity httpEntity,
      boolean put,
      final String contentType) {

    try (final CloseableHttpClient httpclient = ApacheHttpClientUtils.buildHttpClient(username,
        password)) {
      final HttpEntityEnclosingRequestBase httpPost =
          put ? new HttpPut(url) : new HttpPost(url);
      httpPost.setHeader(HttpHeaders.CONTENT_TYPE, contentType);
      if (addBasicAuth) {
        httpPost.setHeader(HttpHeaders.AUTHORIZATION,
            "Basic " + Base64.encodeBase64String(
                (username + ":" + password).getBytes(
                    StandardCharsets.UTF_8)));
      }
      httpPost.setEntity(httpEntity);

      final HttpResponse response = httpclient.execute(httpPost);
      ensureResponseOK(response);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Executes SPARQL ASK against a SPARQL 1.1 Protocol endpoint.
   *
   * @param sparqlServiceUrl SPARQL endpoint
   * @param username     to authenticate with
   * @param password     to authenticate with
   * @param query        SELECT query
   * @param addBasicAuth whether the "Authorization Basic ..." header shall be added
   */
  public static boolean executeAsk(
      final String sparqlServiceUrl,
      final String username,
      final String password,
      final String query,
      final boolean addBasicAuth) {

    try (final CloseableHttpClient httpclient = ApacheHttpClientUtils.buildHttpClient(username,
        password)) {
      final String acceptHeader = APPLICATION_SPARQL_RESULTS_XML;
      final HttpEntityEnclosingRequestBase httpPost = createPost(sparqlServiceUrl, acceptHeader, username, password, addBasicAuth);
      httpPost.setEntity(new StringEntity(query, StandardCharsets.UTF_8));

      final HttpResponse response = httpclient.execute(httpPost);
      ensureResponseOK(response);
      return ResultSetMgr.readBoolean(response.getEntity().getContent(),
          getResultSetLanguage(response, acceptHeader));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Executes SPARQL SELECT against a SPARQL 1.1 Protocol endpoint.
   *
   * @param sparqlServiceUrl SPARQL endpoint
   * @param username     to authenticate with
   * @param password     to authenticate with
   * @param query        ASK query
   * @param addBasicAuth whether the "Authorization Basic ..." header shall be added
   */
  public static <R> R executeSelect(
      final String sparqlServiceUrl,
      final String username,
      final String password,
      final String query,
      final boolean addBasicAuth,
      final Function<ResultSet, R> handler) {

    try (final CloseableHttpClient httpclient = ApacheHttpClientUtils.buildHttpClient(username,
        password)) {
      final String acceptHeader = APPLICATION_SPARQL_RESULTS_XML;
      final HttpEntityEnclosingRequestBase httpPost = createPost(sparqlServiceUrl, acceptHeader, username, password, addBasicAuth);
      httpPost.setEntity(new StringEntity(query, StandardCharsets.UTF_8));

      final HttpResponse response = httpclient.execute(httpPost);
      ensureResponseOK(response);
      return handler.apply(ResultSetMgr.read(response.getEntity().getContent(),
          getResultSetLanguage(response, acceptHeader)).materialise());
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Executes SPARQL CONSTRUCT against a SPARQL 1.1 Protocol endpoint.
   *
   * @param sparqlServiceUrl SPARQL endpoint
   * @param username     to authenticate with
   * @param password     to authenticate with
   * @param query        CONSTRUCT query
   * @param addBasicAuth whether the "Authorization Basic ..." header shall be added
   */
  public static Model executeConstruct(
      final String sparqlServiceUrl,
      final String username,
      final String password,
      final String query,
      final boolean addBasicAuth) {

    try (final CloseableHttpClient httpclient = ApacheHttpClientUtils.buildHttpClient(username,
        password)) {
      final HttpEntityEnclosingRequestBase httpPost = createPost(sparqlServiceUrl, TEXT_TURTLE, username, password, addBasicAuth);
      httpPost.setEntity(new StringEntity(query, StandardCharsets.UTF_8));

      final HttpResponse response = httpclient.execute(httpPost);
      ensureResponseOK(response);

      final Model model = ModelFactory.createDefaultModel();
      model.read(response.getEntity().getContent(), null, Lang.TURTLE.getLabel());
      return model;
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
