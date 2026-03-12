package zone.cogni.asquare.cube.jsondiff;

import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.JsonNode;

import java.util.function.Supplier;

@Setter
@Getter
public class JsonDiffInput {

  private String id;
  private Supplier<JsonNode> from;
  private Supplier<JsonNode> to;

}
