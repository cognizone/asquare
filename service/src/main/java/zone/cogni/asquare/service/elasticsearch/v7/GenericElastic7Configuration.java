package zone.cogni.asquare.service.elasticsearch.v7;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import io.vavr.control.Try;
import zone.cogni.asquare.service.elasticsearch.ElasticConfiguration;

/**
 * Please use @ConfigurationProperties or something more Spring based.
 */
@Deprecated
public abstract class GenericElastic7Configuration implements ElasticConfiguration {

  private Elasticsearch7Store store;
  private final Boolean isIdUrlEncoded;

  protected GenericElastic7Configuration(Boolean isIdUrlEncoded) {
    this.isIdUrlEncoded = isIdUrlEncoded;
  }

  public static ObjectNode getSimpleSettings() {
    String settingsString = "{\"settings\": {\"index\": {\"number_of_shards\": 1, \"number_of_replicas\":1}}}";
    return (ObjectNode) Try.of(() -> new ObjectMapper().readTree(settingsString)).get();
  }

  public abstract String getHost();
  public abstract String getPort();

  protected final void init() {
    Preconditions.checkNotNull(getHost());
    Preconditions.checkNotNull(getPort());
    store = new HttpElasticsearch7Store("http://" + getHost() + ":" + getPort(), isIdUrlEncoded);
  }

  public Elasticsearch7Store getStore(){
    return store;
  }
}
