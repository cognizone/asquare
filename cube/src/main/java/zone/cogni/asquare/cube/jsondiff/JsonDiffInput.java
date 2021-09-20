package zone.cogni.asquare.cube.jsondiff;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.function.Supplier;

public class JsonDiffInput {

  private String id;
  private Supplier<JsonNode> from;
  private Supplier<JsonNode> to;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Supplier<JsonNode> getFrom() {
    return from;
  }

  public void setFrom(Supplier<JsonNode> from) {
    this.from = from;
  }

  public Supplier<JsonNode> getTo() {
    return to;
  }

  public void setTo(Supplier<JsonNode> to) {
    this.to = to;
  }
}
