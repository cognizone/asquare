package zone.cogni.asquare.service.elasticsearch.info;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JSON wrapper from <code>/_stats</code> endpoint of elasticsearch.
 */
class Info_stats {

  private final JsonNode root;

  Info_stats(JsonNode root) {
    this.root = root;
  }

  /**
   * @return sorted list of index names
   */
  List<String> getIndexNames() {
    ObjectNode indexes = (ObjectNode) root.get("indices");
    List<String> result = new ArrayList<>(indexes.propertyNames());
    Collections.sort(result);
    return result;
  }

  /**
   * @param name of index
   * @return node of index in JSON
   */
  JsonNode getIndex(String name) {
    JsonNode indexes = root.get("indices");
    if (!indexes.has(name)) return null;

    return indexes.get(name);
  }

  String getUuid(String indexName) {
    JsonNode index = getIndex(indexName);
    return index.get("uuid").textValue();
  }

  public long getDocumentCount(String indexName) {
    JsonNode index = getIndex(indexName);
    return index.get("total").get("docs").get("count").longValue();
  }

  public long getSizeInBytes(String indexName) {
    JsonNode index = getIndex(indexName);
    return index.get("total").get("store").get("size_in_bytes").longValue();
  }
}
