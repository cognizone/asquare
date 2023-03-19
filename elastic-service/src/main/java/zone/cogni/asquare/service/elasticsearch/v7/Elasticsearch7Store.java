package zone.cogni.asquare.service.elasticsearch.v7;

import com.fasterxml.jackson.databind.node.ObjectNode;
import zone.cogni.asquare.service.elasticsearch.ElasticStore;
import zone.cogni.asquare.service.elasticsearch.Params;

import java.util.List;

public interface Elasticsearch7Store extends ElasticStore {

  // Interfaces for elastic 6 and elastic 7 were merged to facilitate legacy code migration to elastic 7.
  // Elastic 7 provides more generic methods, so it is possible to reuse them
  // safely for legacy code supporting elastic 6 services.

  default void indexDocument(String indexName, String type, String id, ObjectNode document) {
    indexDocument(indexName, id, document);
  }

  default void indexDocument(String indexName, String type, String id, ObjectNode document, Params params) {
    indexDocument(indexName, id, document, params);
  }

  default void deleteDocument(String indexName, String type, String id) {
    deleteDocument(indexName, id);
  }

  default void deleteDocument(String indexName, String type, String id, Params params) {
    deleteDocument(indexName, id, params);
  }

  default ObjectNode getDocumentById(String indexName, String type, String id) {
    return getDocumentById(indexName, id);
  }

  default ObjectNode getDocumentsByIds(String indexName, String type, List<String> ids) {
    return getDocumentsByIds(indexName, ids);
  }

  default ObjectNode deleteByQueryWithAck(String indexName, ObjectNode query, Params params) {
    deleteByQuery(indexName, query);
    return null;
  }
}
