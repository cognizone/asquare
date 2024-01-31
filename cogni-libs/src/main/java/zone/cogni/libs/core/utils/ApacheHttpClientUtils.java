package zone.cogni.libs.core.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
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
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility functions for working with the Apache HttpClient.
 */
public class ApacheHttpClientUtils {

  private final static Logger log = LoggerFactory.getLogger(ApacheHttpClientUtils.class);

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
}
