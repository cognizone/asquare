package zone.cogni.asquare.actionlogger;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.actionlogger.LoggedActionModel;
import zone.cogni.actionlogger.LoggedActionSaver;
import zone.cogni.asquare.service.elasticsearch.ElasticStore;

import java.util.Map;

public class ElasticStoreLoggedActionSaver implements LoggedActionSaver {
  private static final Logger log = LoggerFactory.getLogger(ElasticStoreLoggedActionSaver.class);

  private final ElasticStore elasticStore;
  private final String index;

  public ElasticStoreLoggedActionSaver(ElasticStore elasticStore, String index) {
    this.elasticStore = elasticStore;
    this.index = index;
  }

  @Override
  public void save(Map<String, Object> report) {
    ObjectNode jsonElasticDocument = convertToObjectNode(report);
    try {
      String id = (String) report.get(LoggedActionModel.ReportKeys.id);
      elasticStore.indexDocument(index, id, jsonElasticDocument);
    }
    catch (Exception e) {
      log.warn("Failed to store log to index '{}': {}", index, jsonElasticDocument, e);
    }
  }
}
