package zone.cogni.asquare.service.elasticsearch.v6;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vavr.control.Try;
import zone.cogni.asquare.service.elasticsearch.ElasticConfiguration;

public abstract class GenericElasticConfiguration implements ElasticConfiguration {

  public static ObjectNode getSimpleSettings() {
    String settingsString = "{\"settings\": {\"index\": {\"number_of_shards\": 1, \"number_of_replicas\":1}}}";
    return (ObjectNode) Try.of(() -> new ObjectMapper().readTree(settingsString)).get();
  }

  public abstract String getHost();
  public abstract String getPort();
  private ElasticsearchStore store;
  private final Boolean isIdUrlEncoded;

  protected GenericElasticConfiguration(Boolean isIdUrlEncoded) {
    this.isIdUrlEncoded = isIdUrlEncoded;
  }

  protected void init() {
    store = new HttpElasticsearchStore("http://" + getHost() + ":" + getPort(), isIdUrlEncoded);
  }

  public ElasticsearchStore getStore(){
    return store;
  }
}
