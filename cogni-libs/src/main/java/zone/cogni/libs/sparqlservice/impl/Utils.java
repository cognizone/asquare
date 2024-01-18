package zone.cogni.libs.sparqlservice.impl;

import java.text.MessageFormat;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Utility methods for JDK11 HttpClient.
 */
public class Utils {

  private static final Logger log = LoggerFactory.getLogger(Utils.class);

  /**
   * Creates a basic authenticator (username, password)
   *
   * @param username username
   * @param password password (not null)
   * @return authenticator
   */
  public static Authenticator createBasicAuthenticator(final String username,
      final String password) {
    Objects.requireNonNull(password);
    return new Authenticator() {
      @Override
      protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(username, password.toCharArray());
      }
    };
  }

  /**
   * Creates a new HttpClientBuilder. If both username and password is supplied, basic authentication header is generated.
   *
   * @param username username
   * @param password password
   * @return HttpClient.Builder
   */
  public static HttpClient.Builder createHttpClientBuilder(final String username,
      final String password) {
    final HttpClient.Builder httpClientBuilder = HttpClient.newBuilder();

    if (StringUtils.isNoneBlank(username, password)) {
      httpClientBuilder.authenticator(createBasicAuthenticator(username, password));
    } else {
      log.error("Endpoint credentials not properly configured");
    }
    return httpClientBuilder;
  }

  /**
   * Simple validation of the 2xx success.
   *
   * @param httpResponse to analyze
   */
  public static void checkOK(HttpResponse<?> httpResponse) {
    if (httpResponse.statusCode() / 100 == 2) {
      return;
    }
    throw new RuntimeException(
        MessageFormat.format("Expected 2xx code, but was {0} with body: {1}",
            httpResponse.statusCode(),
            httpResponse.body()));
  }

  /**
   * Executes a request and checks its response for success.
   *
   * @param request HttpRequest to analyze.
   * @param client HttpClient to use.
   */
  public static void execute(final HttpRequest request, final HttpClient client) {
    try {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      checkOK(response);
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}