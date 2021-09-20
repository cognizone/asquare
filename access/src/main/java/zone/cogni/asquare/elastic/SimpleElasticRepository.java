package zone.cogni.asquare.elastic;

import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

@Deprecated
@Repository
public class SimpleElasticRepository {

  @Value("${elasticsearch.cluster-name}")
  private String clusterName;
  @Value("${elasticsearch.url}")
  private String url;
  @Value("${elasticsearch.port}")
  private int port;

  private TransportClient client;

  public void prepareClient() {
    try {
      Settings settings = Settings.builder()
              .put("cluster.name", clusterName).build();
      client = new PreBuiltTransportClient(settings)
              .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(url), port));
    }
    catch (UnknownHostException e) {
      throw new IllegalStateException(e);
    }
  }

  public void deleteIndex(String index) {
    client.admin().indices().delete(new DeleteIndexRequest(index));
  }

  public void prepareIndex(String index, Map<String, String> mappings) {
    try {
      client.admin().indices().prepareGetMappings(index).get();
      updateOrCreateMappings(index, mappings);
    }
    catch (IndexNotFoundException e) {
      client.admin()
              .indices()
              .prepareCreate(index)
              .setSettings(Settings.builder().put("index.number_of_shards", 1))
              .get();
      updateOrCreateMappings(index, mappings);
    }
  }

  private void updateOrCreateMappings(String index, Map<String, String> mappings) {
    mappings.forEach((type, mapping) -> {
      PutMappingRequestBuilder putMappingRequestBuilder = client.admin().indices().preparePutMapping(index);
      putMappingRequestBuilder.setType(type).setSource(mapping, XContentType.JSON);
      putMappingRequestBuilder.get();
    });
  }

  public TransportClient getTransportClient() {
    return client;
  }
}
