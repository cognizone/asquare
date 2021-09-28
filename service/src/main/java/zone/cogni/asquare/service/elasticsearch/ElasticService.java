package zone.cogni.asquare.service.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import zone.cogni.asquare.access.Params;

public abstract class ElasticService<T> {
  private static final Logger log = LoggerFactory.getLogger(ElasticService.class);
  protected final ObjectMapper objectMapper = new ObjectMapper();
  private final ElasticStore store;

  protected ElasticService(ElasticStore store) {
    this.store = store;
  }

  public abstract String getIndex();

  public abstract String getIndexType();

  public void index(T object) {
    ObjectNode doc = objectMapper.valueToTree(object);
    index(getId(object), doc);
  }

  protected void index(String docId, ObjectNode doc) {
    store.indexDocument(getIndex(), docId, doc, new Params().setRefresh(getParamsRefresh()));
  }

  protected abstract String getId(T object);

  protected abstract Params.Refresh getParamsRefresh();

  protected ObjectNode getSimpleSettings() {
    return store.getDefaultSettings();
  }

  protected void ensureIndexExists() {
    try {
      try {
        store.search(getIndex(), objectMapper.createObjectNode());
      }
      catch (HttpClientErrorException ex) {
        if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
          store.createIndex(getIndex(), getSimpleSettings());
        }
        else {
          throw ex;
        }
      }

    }
    catch (Exception e) {
      log.error("The index could not be accessed.", e);
    }
  }

}
