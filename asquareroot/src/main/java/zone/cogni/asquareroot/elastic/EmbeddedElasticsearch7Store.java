package zone.cogni.asquareroot.elastic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.elasticsearch.action.ActionFuture;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.action.support.replication.ReplicatedWriteRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.discovery.DiscoveryModule;
import org.elasticsearch.env.NodeEnvironment;
import org.elasticsearch.node.MockNode;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeValidationException;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.MockHttpTransport;
import org.elasticsearch.transport.nio.MockNioTransportPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.access.Params;
import zone.cogni.asquare.service.elasticsearch.v7.Elasticsearch7Store;
import zone.cogni.asquare.service.elasticsearch.v7.GenericElastic7Configuration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class EmbeddedElasticsearch7Store implements Elasticsearch7Store {

  private static final Logger log = LoggerFactory.getLogger(EmbeddedElasticsearch7Store.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private Node node;
  private Client client;
  private Settings.Builder elasticSettings;

  public EmbeddedElasticsearch7Store(File dataFolder) {
    Setting<String> NODE_NAME_SETTING = Setting.simpleString("node.name", Setting.Property.NodeScope);
    Setting<String> TRANSPORT_TYPE_SETTING = Setting.simpleString("transport.type", Setting.Property.NodeScope);

    elasticSettings =
      Settings.builder()
              .put("network.host", "127.0.0.1")
              .put("path.data", dataFolder.getAbsolutePath())
              .put("path.home", dataFolder.getAbsolutePath())
              .put("cluster.name", "embedded")
              .put(NODE_NAME_SETTING.getKey(), "embedded")
              .put(NodeEnvironment.NODE_ID_SEED_SETTING.getKey(), 0)
              .put(TRANSPORT_TYPE_SETTING.getKey(), MockNioTransportPlugin.MOCK_NIO_TRANSPORT_NAME)
              .put(DiscoveryModule.DISCOVERY_TYPE_SETTING.getKey(), DiscoveryModule.ZEN_DISCOVERY_TYPE);
  }

  public void start() throws NodeValidationException {
    List<Class<? extends Plugin>> classpathPlugins = new ArrayList<>();
    classpathPlugins.add(MockNioTransportPlugin.class);
    classpathPlugins.add(MockHttpTransport.TestPlugin.class);

    node = new MockNode(elasticSettings.build(), classpathPlugins, false);
    node.start();
    client = node.client();
  }

  public void restart() throws NodeValidationException {
    node.start();
  }

  public Client getClient() {
    return client;
  }

  public void close() throws InterruptedException, IOException {
    node.close();
    node.awaitClose(30, TimeUnit.SECONDS);
  }

  public boolean isClosed() {
    return node.isClosed();
  }


  @Override
  public String getUrl() {
    // not sure if this is correct
    return "http://127.0.0.1:9200/";
  }

  @Override
  public ObjectNode getDefaultSettings() {
    return GenericElastic7Configuration.getSimpleSettings();
  }

  @Override
  public void createIndex(String indexName, ObjectNode settings) {
    IndexRequest request = new IndexRequest(indexName);
    request.source(settings, XContentType.JSON);
    ActionFuture<IndexResponse> futureResponse = client.index(request);
    IndexResponse response = futureResponse.actionGet();

    log.info("createIndex response {}", response);
  }

  @Override
  public void deleteIndex(String indexName) {
    DeleteIndexRequest request = new DeleteIndexRequest(indexName);
    ActionFuture<AcknowledgedResponse> futureResponse = client.admin().indices().delete(request);
    AcknowledgedResponse response = futureResponse.actionGet();

    log.info("deleteIndex response {}", response);
  }

  private void indexDocument(IndexRequest request) {

    ActionFuture<IndexResponse> futureResponse = client.index(request);
    IndexResponse response = futureResponse.actionGet();

    log.info("indexDocument response {}", response);

  }


  @Override
  public void indexDocument(String indexName, String id, ObjectNode document) {
    IndexRequest request = new IndexRequest(indexName);
    Map<String, Object> jsonMap = objectMapper.convertValue(document, new TypeReference<Map<String, Object>>() {
    });
    request.id(id).source(jsonMap);
    indexDocument(request);
  }

  private void applyParams(ReplicatedWriteRequest request, Params params) {
    if (!params.isEmpty()) {
      switch (params.toMultiValueMap().getFirst(Params.REFRESH)) {
        case "true":
          request.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
          break;
        case "wait_for":
          request.setRefreshPolicy(WriteRequest.RefreshPolicy.WAIT_UNTIL);
          break;
        case "false":
          request.setRefreshPolicy(WriteRequest.RefreshPolicy.NONE);
          break;
      }
    }
  }

  @Override
  public void indexDocument(String indexName, String id, ObjectNode document, Params params) {
    IndexRequest request = new IndexRequest(indexName);
    Map<String, Object> jsonMap = objectMapper.convertValue(document, new TypeReference<Map<String, Object>>() {
    });
    request.id(id).source(jsonMap);
    applyParams(request, params);
    indexDocument(request);
  }

  @Override
  public ObjectNode getDocumentById(String indexName, String id) {
    GetRequest request = new GetRequest(indexName, id);

    ActionFuture<GetResponse> futureResponse = client.get(request);
    GetResponse getResponse = futureResponse.actionGet();

    return objectMapper.valueToTree(getResponse);
  }

  @Override
  public ObjectNode getDocumentById(String indexName, String id, Params params) {
    throw new RuntimeException("Refresh policy is not supported for get operation please use method getDocumentById without Params");
  }

  @Override
  public ObjectNode getDocumentsByIds(String indexName, Collection<String> ids) {
    MultiGetRequest request = new MultiGetRequest();

    for (String id : ids) {
      request.add(new MultiGetRequest.Item(indexName, id));

    }
    ActionFuture<MultiGetResponse> responseFuture = client.multiGet(request);
    MultiGetResponse response = responseFuture.actionGet();
    List<Object> sources = Arrays.stream(response.getResponses())
                                 .map(item -> item.getResponse())
                                 .collect(Collectors.toList());
    return objectMapper.valueToTree(sources);
  }

  @Override
  public ObjectNode getDocumentsByIds(String indexName, Collection<String> ids, Params params) {
    throw new RuntimeException("Refresh policy is not supported for get operation please use method getDocumentsByIds without Params");
  }

  @Override
  public void deleteDocument(String indexName, String id) {

  }

  @Override
  public void deleteDocument(String indexName, String id, Params params) {

  }

  @Override
  public void deleteByQuery(String indexName, ObjectNode query) {

  }

  @Override
  public void deleteByQuery(String indexName, ObjectNode query, Params params) {

  }

  @Override
  public ObjectNode search(String indexName, ObjectNode searchObject) {
    return null;
  }
}
