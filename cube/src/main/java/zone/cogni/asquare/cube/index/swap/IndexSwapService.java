package zone.cogni.asquare.cube.index.swap;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import zone.cogni.asquare.service.elasticsearch.ElasticStore;
import zone.cogni.asquare.service.elasticsearch.info.ElasticsearchMetadata;
import zone.cogni.asquare.service.elasticsearch.info.ElasticsearchMetadataService;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.stream.Collectors;

import static zone.cogni.asquare.cube.index.swap.JsonBuilder.array;
import static zone.cogni.asquare.cube.index.swap.JsonBuilder.object;

public class IndexSwapService {

  private static final Logger log = LoggerFactory.getLogger(IndexSwapService.class);

  private final ElasticStore elasticStore;
  private final ElasticsearchMetadataService elasticsearchMetadataService;

  private final RestTemplate restTemplate = new RestTemplate(clientHttpRequestFactory(3000, 10000));

  @SuppressWarnings("SameParameterValue")
  private ClientHttpRequestFactory clientHttpRequestFactory(int connectTimeout, int readTimeout) {
    HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeout);
    factory.setReadTimeout(readTimeout);
    return factory;
  }

  public IndexSwapService(ElasticStore elasticStore,
                          ElasticsearchMetadataService elasticsearchMetadataService) {
    this.elasticStore = elasticStore;
    this.elasticsearchMetadataService = elasticsearchMetadataService;
  }

  public ElasticsearchMetadata.Index getIndexForAlias(@Nonnull String alias) {
    ElasticsearchMetadata elasticsearchMetadata = elasticsearchMetadataService.getElasticsearchMetadata(elasticStore);
    List<ElasticsearchMetadata.Index> indexes =
            elasticsearchMetadata.getIndexes()
                                 .stream()
                                 .filter(index -> index.getAliases().contains(alias))
                                 .collect(Collectors.toList());

    if (indexes.size() > 1) {
      String indexNames = indexes.stream().map(ElasticsearchMetadata.Index::getName).collect(Collectors.joining(", "));
      throw new RuntimeException("multiple indexes found for alias '" + alias + "' has indexes " + indexNames);
    }

    return indexes.isEmpty() ? null
                             : indexes.get(0);
  }

  public IndexSwapState getState(String aliasName, String indexPrefix) {
    ElasticsearchMetadata metadata = elasticsearchMetadataService.getElasticsearchMetadata(elasticStore);

    IndexSwapState indexSwapState = new IndexSwapState();
    indexSwapState.setAliasName(aliasName);
    indexSwapState.setIndexPrefix(indexPrefix);
    indexSwapState.setIndexesMatchingAlias(calculateIndexesMatchingAlias(metadata, aliasName));
    indexSwapState.setIndexesMatchingPrefix(calculateIndexesMatchingPrefix(metadata, indexPrefix));
    indexSwapState.configure();

    return indexSwapState;
  }

  private List<ElasticsearchMetadata.Index> calculateIndexesMatchingAlias(ElasticsearchMetadata metadata,
                                                                          String aliasName) {
    return metadata.getIndexes()
                   .stream()
                   .filter(index -> index.getAliases().contains(aliasName))
                   .collect(Collectors.toList());
  }

  private List<ElasticsearchMetadata.Index> calculateIndexesMatchingPrefix(ElasticsearchMetadata metadata,
                                                                           String indexPrefix) {
    return metadata.getIndexes()
                   .stream()
                   .filter(index -> index.getName().startsWith(indexPrefix))
                   .collect(Collectors.toList());
  }

  public void swap(IndexSwapState indexSwapState) {
    if (log.isInfoEnabled()) {
      log.info("(swap) for alias '{}': old '{}' and new '{}'",
               indexSwapState.getAliasName(),
               indexSwapState.hasIndexMatchingAliasAndPrefix() ? indexSwapState.getIndexMatchingAliasAndPrefix() : "N/A",
               indexSwapState.getNewIndexName());
    }

    String path = elasticStore.getUrl() + "/_aliases";
    ObjectNode apiCall = getJson(indexSwapState);
    log.info("(swap) request: {}", apiCall);
    ObjectNode result = restTemplate.postForObject(path, apiCall, ObjectNode.class);
    log.info("(swap) response: {}", result);
    log.info("(swap) done");
  }

  /**
   * See https://www.elastic.co/guide/en/elasticsearch/reference/current/aliases.html#multiple-actions
   *
   * @param indexSwapState contains all information on swapping process
   * @return JSON based on current
   */
  private ObjectNode getJson(IndexSwapState indexSwapState) {
    // array of operations
    // adding alias to new index
    JsonBuilder.ArrayNodeBuilder array = array().with(
            object().with("add",
                          object().with("index", indexSwapState.getNewIndexName())
                                  .with("alias", indexSwapState.getAliasName()))
    );

    // removing alias from old index (if present)
    if (indexSwapState.hasIndexMatchingAliasAndPrefix()) {
      array.with(
              object().with("remove",
                            object().with("index", indexSwapState.getIndexMatchingAliasAndPrefix().getName())
                                    .with("alias", indexSwapState.getAliasName()))
      );
    }

    // wrap and return
    return object().with("actions", array)
                   .end();
  }

}
