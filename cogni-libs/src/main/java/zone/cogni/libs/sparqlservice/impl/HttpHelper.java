package zone.cogni.libs.sparqlservice.impl;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

class HttpHelper {

  public static HttpClientContext createAuthenticationHttpContext(String user, String password) {
    CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(user, password));

    HttpClientContext context = HttpClientContext.create();
    context.setCredentialsProvider(credentialsProvider);
    context.setAuthCache(new BasicAuthCache()); //so after first call it will know it has to send the authentication
    return context;
  }

  public static void executeAndConsume(Request request) {
    try {
      Response response = request.execute();
      checkAndDiscardResponse(response);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void checkAndDiscardResponse(Response response) throws IOException {
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

  public static String getBody(HttpResponse httpResponse) {
    try {
      return IOUtils.toString(httpResponse.getEntity().getContent(), StandardCharsets.UTF_8);
    }
    catch (IOException e) {
      return "Failed to get error body: " + e.getMessage();
    }
  }

}
