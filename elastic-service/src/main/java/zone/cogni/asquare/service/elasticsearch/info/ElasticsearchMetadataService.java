package zone.cogni.asquare.service.elasticsearch.info;

import tools.jackson.databind.JsonNode;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import zone.cogni.asquare.service.elasticsearch.ElasticStore;

public class ElasticsearchMetadataService {

  private static final Logger log = LoggerFactory.getLogger(ElasticsearchMetadataService.class);

  private final RestTemplate restTemplate;

  public ElasticsearchMetadataService(ElasticsearchMetadata.Configuration configuration) {
    this.restTemplate = calculateRestTemplate(configuration);
  }

  private RestTemplate calculateRestTemplate(ElasticsearchMetadata.Configuration configuration) {
    RequestConfig requestConfig = RequestConfig.custom()
                                               .setResponseTimeout(Timeout.ofMilliseconds(configuration.getReadTimeout())) // setConnectionTimeout seems to be deprecated
                                               .setConnectionRequestTimeout(Timeout.ofMilliseconds(configuration.getConnectTimeout()))
                                               .build();

    CloseableHttpClient httpClient = HttpClients.custom()
                                                .setDefaultRequestConfig(requestConfig)
                                                .build();

    HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);

    return new RestTemplate(factory);
  }

  public ElasticsearchMetadata getElasticsearchMetadata(ElasticStore elasticStore) {
    return new ElasticsearchMetadata(
            getJson(elasticStore, "_stats"),
            getJson(elasticStore, "_cluster/state")
    );
  }

  private JsonNode getJson(ElasticStore elasticStore, String relativePart) {
    String url = getUrl(elasticStore.getUrl(), relativePart);
    log.info("(fetching json) url {}", url);
    return restTemplate.getForObject(url, JsonNode.class);
  }

  private String getUrl(String elasticStoreUrl, String relativePart) {
    if (relativePart.startsWith("/")) throw new RuntimeException("please do not start with '/' in " + relativePart);
    return elasticStoreUrl
           + (elasticStoreUrl.endsWith("/") ? "" : "/")
           + relativePart;
  }

}
