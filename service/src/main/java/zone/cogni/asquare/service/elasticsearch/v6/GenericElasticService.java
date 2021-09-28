package zone.cogni.asquare.service.elasticsearch.v6;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.service.elasticsearch.ElasticService;

public abstract class GenericElasticService<T> extends ElasticService<T> {
  private static final Logger log = LoggerFactory.getLogger(GenericElasticService.class);

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ElasticsearchStore elasticsearchStore;

  protected GenericElasticService(ElasticsearchStore elasticsearchStore) {
    super(elasticsearchStore);
    this.elasticsearchStore = elasticsearchStore;
  }

  public ElasticsearchStore getElasticsearchStore() {
    return elasticsearchStore;
  }

}
