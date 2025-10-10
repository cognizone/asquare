package zone.cogni.asquare.service.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Nonnull;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;

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
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
            .query(QueryBuilders.termQuery("data.type.keyword", typeClassId))
            .fetchSource(true);
    try {
      return (ObjectNode) new ObjectMapper().readTree(searchSourceBuilder.toString());
    }
    catch (IOException e) {
      throw new RuntimeException("Failed to create ObjectNode from SearchSourceBuilder", e);
    }
  }
}