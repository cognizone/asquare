package zone.cogni.asquare.service.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;

public enum ElasticHelper {
  ;

  @Nonnull
  public static ObjectNode queryBuildToSearchRequest(@Nonnull QueryBuilder queryBuilder) {
    return queryBuildToSearchRequest(queryBuilder, null);
  }

  @Nonnull
  public static ObjectNode queryBuildToSearchRequest(@Nonnull QueryBuilder queryBuilder, @Nullable Integer size) {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(queryBuilder);
    if (null != size) searchSourceBuilder.size(size);
    try {
      return (ObjectNode) new ObjectMapper().readTree(searchSourceBuilder.toString());
    }
    catch (IOException e) {
      throw new RuntimeException("Failed to create ObjectNode from SearchSourceBuilder", e);
    }
  }
}