package zone.cogni.asquare.service.queryapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.util.MultiValueMap;
import zone.cogni.asquare.service.queryapi.filter.ResourceFilter;

import java.util.Optional;

public class ResourceQuery {

  public static ResourceQuery fromQueryParams(MultiValueMap<String, String> queryParams, String type, String uri) {
    return new ResourceQuery(type, uri, ResourceFilter.fromQueryParams(queryParams));
  }

  private final String type;
  private final String uri;
  private final ResourceFilter filter;

  public ResourceQuery(String type) {
    this.filter = new ResourceFilter();
    this.type = type;
    uri = null;
  }

  public ResourceQuery(String type, String uri) {
    this.filter = new ResourceFilter();
    this.type = type;
    this.uri = uri;
  }

  public ResourceQuery(String type, ResourceFilter filter) {
    this.filter = filter;
    this.type = type;
    uri = null;
  }

  public ResourceQuery(String type, String uri, ResourceFilter filter) {
    this.filter = filter;
    this.type = type;
    this.uri = uri;
  }

  public String getType() {
    return type;
  }

  public String getUri() {
    return uri;
  }

  public ResourceFilter getFilter() {
    return filter;
  }

  public static ResourceQuery fromJson(ObjectNode json) {
    String uri = Optional.ofNullable(json.get("uri")).map(JsonNode::asText).orElse(null);
    String type = json.get("type").asText();

    ResourceFilter filter = ResourceFilter.fromJson((ObjectNode) json.get("filter"));
    return new ResourceQuery(type, uri, filter);
  }

  public ObjectNode toJson() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode root = mapper.createObjectNode();

    root.put("type", type);
    if (uri != null) root.put("uri", uri);
    root.set("filter", filter.toJson(mapper));

    return root;
  }

  public void attachToJsonResponse(ObjectNode response) {

    response.set("query", toJson());
  }
}

