package zone.cogni.asquare.service.elasticsearch.v7;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Streams;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.service.elasticsearch.ElasticService;
import zone.cogni.asquare.access.Params;

import java.util.List;
import java.util.stream.Collectors;

public abstract class GenericElastic7Service<T> extends ElasticService<T> {
  private static final Logger log = LoggerFactory.getLogger(GenericElastic7Service.class);

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Elasticsearch7Store elasticsearchStore;

  protected GenericElastic7Service(Elasticsearch7Store elasticsearchStore) {
    super(elasticsearchStore);
    this.elasticsearchStore = elasticsearchStore;
  }

  protected abstract Class<T> getObjectClass();

  public Elasticsearch7Store getElasticsearchStore() {
    return elasticsearchStore;
  }

  public T get(String id) {
    // todo : better NotFoundException handling
    ObjectNode result = (ObjectNode) elasticsearchStore.getDocumentById(getIndex(), id).get("_source");
    return toPojo(result);
  }

  private T toPojo(ObjectNode source) {
    return Try.of(() -> objectMapper.treeToValue(source, getObjectClass()))
              .getOrElseThrow((ex) -> new IllegalStateException(ex));
  }

  public List<T> search(ObjectNode query) {
    return resultToList(rawSearch(query));
  }

  public ObjectNode rawSearch(ObjectNode query) {
    return elasticsearchStore.search(getIndex(), query);
  }

  public void delete(String id) {
    elasticsearchStore.deleteDocument(getIndex(), id, new Params().setRefresh(getParamsRefresh()));
  }

  private List<T> resultToList(ObjectNode result) {
    ArrayNode list = (ArrayNode) Option.of(result.get("hits"))
                                       .flatMap(hits -> Option.of(hits.get("hits")))
                                       .orElse(() -> Option.of(result.get("docs")))
                                       .getOrElseThrow(IllegalStateException::new);

    return Streams.stream(list.elements())
                  .map(doc -> doc.get("_source"))
                  .map(source -> toPojo((ObjectNode) source))
                  .collect(Collectors.toList());
  }
}
