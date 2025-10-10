package zone.cogni.asquare.service.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Nonnull;

public enum ElasticHelper {
  ;

  // NOTE: queryBuildToSearchRequest(QueryBuilder, Integer size) methods removed due to licensing.
  //
  // These methods converted Elasticsearch QueryBuilder objects to JSON (ObjectNode) via
  // SearchSourceBuilder serialization. Optionally added a size parameter.
  //
  // Replacement: Build Elasticsearch query JSON directly using Jackson ObjectNode instead.

  @Nonnull
  public static ObjectNode buildFindAllQuery(@Nonnull String typeClassId) {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode root = mapper.createObjectNode();

    // Build query.term["data.type.keyword"]
    ObjectNode query = root.putObject("query");
    ObjectNode term = query.putObject("term");
    ObjectNode termField = term.putObject("data.type.keyword");
    termField.put("value", typeClassId);
    termField.put("boost", 1.0);

    // Build _source with empty includes/excludes arrays
    ObjectNode source = root.putObject("_source");
    source.putArray("includes");
    source.putArray("excludes");

    return root;
  }
}