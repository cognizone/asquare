package zone.cogni.asquare.service.elasticsearch.v6;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import io.vavr.Tuple2;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.lang.Nullable;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.UnknownHttpStatusCodeException;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import zone.cogni.asquare.access.Params;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;


public class HttpElasticsearchStore implements ElasticsearchStore {

  private static final Logger log = LoggerFactory.getLogger(HttpElasticsearchStore.class);
  private static final long numberOfTries = 3;
  private static final long sleepInMillis = 500;
  private final RestTemplate restTemplate = new RestTemplate(clientHttpRequestFactory(60000, 5000));
  private final String url;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Boolean urlEncodedId;

  @Override
  public String getUrl() {
    return url;
  }

  public ObjectNode getDefaultSettings() {
    return GenericElasticConfiguration.getSimpleSettings();
  }

  public HttpElasticsearchStore(String url, Boolean urlEncodedId) {
    this.url = url;
    this.urlEncodedId = urlEncodedId;
    restTemplate.setErrorHandler(new ElasticErrorHandler());
  }

  @Override
  public void createIndex(String indexName, ObjectNode settings) {
    String path = String.join("/", url, indexName);

    restTemplate.put(path, settings);
  }

  @Override
  public void deleteIndex(String indexName) {
    String path = String.join("/", url, indexName);

    try {
      restTemplate.delete(path);
    }
    catch (ElasticClientError e) {
      if (e.getRawStatusCode() == 404) {
        log.info("Tried to delete index '{}', but it didn't exist", indexName);
        return;
      }

      throw e;
    }
  }

  @Override
  public void indexDocument(String indexName, String type, String id, ObjectNode document) {
    indexDocument(indexName, type, id, document, null);
  }

  @Override
  public void indexDocument(String indexName, String type, String id, ObjectNode document, Params params) {
    URI uri = createUri(indexName, type, id, params);

    byte[] bytes = Try.of(() -> new ObjectMapper().writeValueAsBytes(document)).get();

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<byte[]> entity = new HttpEntity<>(bytes, headers);

    retryIfNeeded(new Tuple2<>(uri, entity),
                  tuple -> restTemplate.exchange(tuple._1, HttpMethod.POST, tuple._2, Void.class));
  }

  @Override
  public void deleteByQuery(String indexName, ObjectNode query) {
    String path = getPathFor(indexName, Operation._delete_by_query);
    restTemplate.postForObject(path, query, ObjectNode.class);
  }

  @Override
  public void deleteDocument(String indexName, String type, String id) {
    deleteDocument(indexName, type, id, null);
  }

  @Override
  public void deleteDocument(String indexName, String type, String id, Params params) {
    URI uri = createUri(indexName, type, id, params);
    restTemplate.delete(uri);
  }

  @Override
  public ObjectNode getDocumentById(String indexName, String type, String id) {
    URI uri = createUri(indexName, type, id, null);

    ObjectNode response = restTemplate.getForObject(uri, ObjectNode.class);
    Preconditions.checkNotNull(response);

    return (ObjectNode) response.get("_source");
  }

  @Override
  public ObjectNode search(String indexName, ObjectNode searchObject) {
    String path = getPathFor(indexName, Operation._search);
    return restTemplate.postForObject(path, searchObject, ObjectNode.class);
  }

  private <T> void retryIfNeeded(T parameters, Consumer<T> retryable) {
    long tries = 0;
    RestClientException lastException = null;

    while (tries < numberOfTries) {
      try {
        retryable.accept(parameters);
        return;
      }
      catch (RestClientException e) {
        log.warn("Operation failed: {}", e.getMessage());

        lastException = e;
        tries += 1;
        sleep(tries * sleepInMillis);
      }
    }

    throw lastException;
  }

  private void sleep(long millis) {
    try {
      Thread.sleep(millis);
    }
    catch (InterruptedException ignore) {
    }
  }

  private String getPathFor(String indexName, Operation operation) {
    return String.join("/", url, indexName, operation.name());
  }

  public ObjectNode getDocumentsByIds(String indexName, String type, List<String> ids) {
    ObjectNode mgetBody = objectMapper.createObjectNode().putPOJO("ids", ids);
    String path = String.join("/", url, indexName, type, "_mget");

    return restTemplate.postForObject(path, mgetBody, ObjectNode.class);
  }

  private ClientHttpRequestFactory clientHttpRequestFactory(int readTimeout, int connectTimeout) {
    HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();

    factory.setReadTimeout(readTimeout);
    factory.setConnectTimeout(connectTimeout);

    return factory;
  }

  private URI createUri(String indexName, String type, String id, Params params) {

    if (!urlEncodedId) {// a-square v 0.1.0 with single url encoding
      String path = String.join("/", url, indexName, type);
      UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(path);
      if (id != null) {
        String formattedId = Try.of(() -> URLEncoder.encode(id, StandardCharsets.UTF_8.displayName()))
                                .getOrElseThrow((Function<Throwable, RuntimeException>) RuntimeException::new);
        builder.path("/" + formattedId);
      }
      if (params != null && !params.isEmpty()) builder.queryParams(params.toMultiValueMap());

      UriComponents components = builder.build(true);
      return components.toUri();
    }

    // a-square v 0.2.0 with double encoding

    UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);

    builder.path(String.join("/", "", indexName, type));

    if (id != null) builder.path("/" + urlEncode(id));
    if (params != null && !params.isEmpty()) builder.queryParams(params.toMultiValueMap());

    UriComponents components = builder.build(false);
    return components.toUri();
  }

  private String urlEncode(String part) {
    return Try.of(() -> URLEncoder.encode(part, StandardCharsets.UTF_8.displayName())).get();
  }

  private enum Operation {
    _delete_by_query,
    _search
  }

  private static final class ElasticErrorHandler extends DefaultResponseErrorHandler {

    @Override
    protected void handleError(ClientHttpResponse response, HttpStatus statusCode) throws IOException {
      switch (statusCode.series()) {
        case CLIENT_ERROR:
          throw new ElasticClientError(statusCode, response.getStatusText(),
                                       response.getHeaders(), getResponseBody(response), getCharset(response));
        case SERVER_ERROR:
          throw new HttpServerErrorException(statusCode, response.getStatusText(),
                                             response.getHeaders(), getResponseBody(response), getCharset(response));
        default:
          throw new UnknownHttpStatusCodeException(statusCode.value(), response.getStatusText(),
                                                   response.getHeaders(), getResponseBody(response), getCharset(response));
      }
    }
  }

  public static class ElasticClientError extends HttpClientErrorException {

    public ElasticClientError(HttpStatus statusCode, String statusText,
                              @Nullable HttpHeaders responseHeaders, @Nullable byte[] responseBody, @Nullable Charset responseCharset) {
      super(statusCode, statusText, responseHeaders, responseBody, responseCharset);
    }


    public JsonNode getResponse() {
      return Try.of(() -> new ObjectMapper().readTree(getResponseBodyAsByteArray())).get();
    }

    public String getResponseAsString() {
      return Try.of(() -> new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(getResponse())).get();
    }

    @Override
    public String getMessage() {
      return String.join(System.getProperty("line.separator"), super.getMessage(), getResponseAsString());
    }


  }
}
