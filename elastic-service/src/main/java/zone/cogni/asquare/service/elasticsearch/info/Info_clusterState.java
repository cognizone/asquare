package zone.cogni.asquare.service.elasticsearch.info;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import zone.cogni.core.util.DateFormats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * JSON wrapper from <code>cluster/_state</code> endpoint of elasticsearch.
 */
public class Info_clusterState {

  private final JsonNode root;

  public Info_clusterState(JsonNode root) {
    this.root = root;
  }

  public JsonNode getIndex(String name) {
    return root.get("metadata").get("indices").get(name);
  }

  public List<String> getIndexNames() {
    ObjectNode indexes = (ObjectNode) root.get("metadata").get("indices");
    List<String> result = new ArrayList<>(indexes.propertyNames());
    Collections.sort(result);
    return result;
  }

  public String getCreatedDate(String indexName) {
    JsonNode index = getIndex(indexName);
    String timestampAsString = index.get("settings").get("index").get("creation_date").textValue();
    Date date = new Date(Long.parseLong(timestampAsString));
    return DateFormats.formatXsdDateTimeFormat(date);
  }

  public List<String> getAliases(String indexName) {
    JsonNode index = getIndex(indexName);
    JsonNode aliases = index.get("aliases");

    if (!aliases.isArray()) return Collections.emptyList();

    List<String> result = new ArrayList<>();
    ArrayNode aliasArray = (ArrayNode) aliases;
    for (int i = 0; i < aliasArray.size(); i++) {
      JsonNode alias = aliasArray.get(i);
      result.add(alias.textValue());
    }

    return result;
  }
}
