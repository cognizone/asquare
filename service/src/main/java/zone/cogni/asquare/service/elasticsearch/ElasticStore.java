package zone.cogni.asquare.service.elasticsearch;

import com.fasterxml.jackson.databind.node.ObjectNode;
import zone.cogni.asquare.access.Params;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public interface ElasticStore {

  String getUrl();

  ObjectNode getDefaultSettings();

  // Common 6 & 7
  void createIndex (String indexName, ObjectNode settings); // 6 & 7
  void deleteIndex(String indexName); // 6 & 7
  void deleteByQuery(String indexName, ObjectNode query); // 6 & 7
  ObjectNode search(String indexName, ObjectNode searchObject); // 6 & 7

  // 7 Only

  ObjectNode deleteByQueryWithAck(String indexName, ObjectNode query, Params params); // 7
  void deleteByQuery(String indexName, ObjectNode query, Params params);
  void indexDocument(String indexName, String id, ObjectNode document, Params params); // 7
  void indexDocument(String indexName, String id, ObjectNode document); // 7
  void deleteDocument(String indexName, String id); // 7
  void deleteDocument(String indexName, String id, Params params); // 7
  ObjectNode getDocumentById(String indexName, String id); // 7
  ObjectNode getDocumentById(String indexName, String id, Params params); // 7
  ObjectNode getDocumentsByIds (String indexName, Collection<String> ids); // 7
  ObjectNode getDocumentsByIds (String indexName, Collection<String> ids, Params params); // 7

  default Optional<ObjectNode> getOptionalDocumentById(String indexName, String id) {
    //Need to add nice error handling, but if NPE happens, then most likely something is wrong with the Elasticsearch instance or wrong indexName is passed
    try {
      ObjectNode result = (ObjectNode) getDocumentsByIds(indexName, Collections.singletonList(id)).get("docs").get(0);
      return result.get("found").booleanValue() ? Optional.of(result) : Optional.empty();
    }
    catch (NullPointerException npe) {
      throw new RuntimeException("NPE while getting document by id, most likely something is wrong with the Elasticsearch instance or wrong indexName is passed: " +
                                 "request for '"+ id+"' in index '" + indexName + "'", npe);
    }
  }

  // 6 Only
  void indexDocument(String indexName, String type, String id, ObjectNode document); // 6
  void indexDocument(String indexName, String type, String id, ObjectNode document, Params params); // 6
  void deleteDocument(String indexName, String type, String id); // 6
  void deleteDocument(String indexName, String type, String id, Params params); // 6
  ObjectNode getDocumentById (String indexName, String type, String id); // 6
  ObjectNode getDocumentsByIds (String indexName, String type, List<String> ids); // 6
}
