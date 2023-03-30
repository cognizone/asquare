package zone.cogni.asquare.service.elasticsearch;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public class Params {

  /**
   * The request {@code refresh} query parameter name.
   *
   * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-refresh.html</a>
   */
  public static final String REFRESH = "refresh";

  private String graph;
  private long timestamp= -1;

  private MultiValueMap<String, String> params;

  public Params() {
    this.params = new LinkedMultiValueMap<>();
  }

  public Params(MultiValueMap<String, String> params) {
    this.params = new LinkedMultiValueMap<>(params);
  }

  private static Params refreshParams(Refresh refresh) {
    Params params = new Params();
    params.setRefresh(refresh);
    return params;
  }

  public Params filterParams() {
    Params params = new Params(this.params);
    return params;
  }

  public static Params noRefresh() {
    return refreshParams(Refresh._false);
  }

  public static Params waitFor() {
    return refreshParams(Refresh.wait_for);
  }

  public static Params refresh() {
    return refreshParams(Refresh._true);
  }

  public Params setRefresh(Refresh refresh) {
    return setParam(REFRESH, refresh.value);
  }

  public Params setParam(String name, String value) {
    params.set(name, value);
    return this;
  }

  public boolean hasGraph() {
    return StringUtils.isNotBlank(graph);
  }

  public boolean hasTimestamp() {
    return timestamp != -1;
  }

  public String getGraph() {
    return graph;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public Params withGraph(String graph) {
    this.graph = graph;
    return this;
  }

  public Params withTimestamp(long timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  public boolean isEmpty() {
    return params.isEmpty();
  }

  public MultiValueMap<String, String> toMultiValueMap() {
    return new LinkedMultiValueMap<>(params);
  }

  public enum Refresh {
    _true("true"),
    wait_for("wait_for"),
    _false("false");

    String value;

    Refresh(String value) {
      this.value = value;
    }
  }


}
