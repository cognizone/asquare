package zone.cogni.asquareroot.elastic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import zone.cogni.asquare.service.elasticsearch.v7.GenericElastic7Configuration;

import java.io.File;

public class EmbeddedElasticTest {

  private EmbeddedElasticsearch7Store embeddedElasticsearch7Store;

  // TODO figure out why test hangs
//  @Test
  public void search_open_tracing_traces_by_operationName(@TempDir File tempDir) throws Exception {

    embeddedElasticsearch7Store = new EmbeddedElasticsearch7Store(tempDir);
    embeddedElasticsearch7Store.start();

    embeddedElasticsearch7Store.createIndex("posts", GenericElastic7Configuration.getSimpleSettings());

    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode doc = objectMapper.createObjectNode();
    doc.put("user", "kimchy");
    doc.put("postDate", "2013-01-30");
    doc.put("message", "trying out Elasticsearch");

    embeddedElasticsearch7Store.indexDocument("posts", "1", doc);
    ObjectNode node = embeddedElasticsearch7Store.getDocumentById("posts", "1");
    Assertions.assertEquals("kimchy", node.get("source").get("user").asText());


    embeddedElasticsearch7Store.close();

  }
}
