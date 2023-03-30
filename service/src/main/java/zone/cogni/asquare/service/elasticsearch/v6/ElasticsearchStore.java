package zone.cogni.asquare.service.elasticsearch.v6;

import com.fasterxml.jackson.databind.node.ObjectNode;
import zone.cogni.asquare.service.elasticsearch.ElasticStore;
import zone.cogni.asquare.service.elasticsearch.Params;

import java.util.Collection;
import java.util.stream.Collectors;


public interface ElasticsearchStore extends ElasticStore {

  // Interfaces for elastic 6 and elastic 7 were merged to facilitate legacy code migration to elastic 7.
  // Elastic 6 has methods with "type" attribute which is not supported in elastic 7
  //
  // All these methods can be safely reimplemented in elastic 6 environments.

  // Default type value is similar to one hardcoded into HttpElasticsearch7Store, identical to all a-square v 0.2.* projects
  String DEFAULT_TYPE = "_doc";


  default void deleteByQuery(String indexName, ObjectNode query, Params params) { deleteByQuery(indexName, query); }

  default void indexDocument(String indexName, String id, ObjectNode document, Params params) {
    indexDocument(indexName, DEFAULT_TYPE, id, document, params);
  }

  default void indexDocument(String indexName, String id, ObjectNode document) {
    indexDocument(indexName, DEFAULT_TYPE, id, document);
  }

  default void deleteDocument(String indexName, String id) {
    deleteDocument(indexName, DEFAULT_TYPE, id);
  }

  default void deleteDocument(String indexName, String id, Params params) {
    deleteDocument(indexName, DEFAULT_TYPE, id, params);
  }

  default ObjectNode getDocumentById(String indexName, String id) {
    return getDocumentById(indexName, DEFAULT_TYPE, id);
  }

  default ObjectNode getDocumentById(String indexName, String id, Params params) {
    return getDocumentById(indexName, DEFAULT_TYPE, id);
  }

  default ObjectNode getDocumentsByIds(String indexName, Collection<String> ids) {
    return getDocumentsByIds(indexName, DEFAULT_TYPE, ids.stream().collect(Collectors.toList()));
  }

  default ObjectNode getDocumentsByIds(String indexName, Collection<String> ids, Params params) {
    return getDocumentsByIds(indexName, DEFAULT_TYPE, ids.stream().collect(Collectors.toList()));
  }

  default ObjectNode deleteByQueryWithAck(String indexName, ObjectNode query, Params params) {
    deleteByQuery(indexName, query, params);
    return null;
  }
}
