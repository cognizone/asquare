package zone.cogni.asquare.service.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Nonnull;

public enum ElasticHelper {
  ;

  // NOTE: queryBuildToSearchRequest methods have been removed due to Elasticsearch licensing concerns.
  //
  // OLD CODE (removed):
  //   queryBuildToSearchRequest(QueryBuilder queryBuilder)
  //   queryBuildToSearchRequest(QueryBuilder queryBuilder, Integer size)
  //
  // These methods took an Elasticsearch QueryBuilder object, converted it to SearchSourceBuilder,
  // then serialized it to JSON (ObjectNode). The optional size parameter set the result size.
  //
  // MIGRATION GUIDE:
  // Build your Elasticsearch query JSON directly using Jackson ObjectNode instead of QueryBuilder.
  //
  // Example replacement:
  //   OLD: ElasticHelper.queryBuildToSearchRequest(QueryBuilders.matchQuery("field", "value"), 10)
  //   NEW: ObjectMapper mapper = new ObjectMapper();
  //        ObjectNode query = mapper.createObjectNode();
  //        query.putObject("query").putObject("match").put("field", "value");
  //        query.put("size", 10);
  //
  // This approach avoids the Elasticsearch JAR dependency (Elastic License 2.0) and keeps the
  // project Apache 2.0 compatible.

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