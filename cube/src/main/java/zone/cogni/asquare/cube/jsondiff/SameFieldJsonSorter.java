package zone.cogni.asquare.cube.jsondiff;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

public class SameFieldJsonSorter implements JsonSorter {

  private String[] idFields;

  public SameFieldJsonSorter(String... idFields) {
    this.idFields = idFields;
  }

  public String[] getIdFields() {
    return idFields;
  }

  public void setIdFields(String[] idFields) {
    this.idFields = idFields;
  }

  @Override
  public Function<ObjectNode, String> getId() {
    return node -> Stream.of(idFields)
                         .filter(node::hasNonNull)
                         .map(idField -> node.get(idField).textValue())
                         .findFirst()
                         .orElse(null);
  }

  @Override
  public BiFunction<ObjectNode, ObjectNode, Boolean> getComparator() {
    return (fromNode, toNode) -> {
      String field = Stream.of(idFields)
                           .filter(idField -> fromNode.hasNonNull(idField) && toNode.hasNonNull(idField))
                           .findFirst()
                           .orElse(null);


      if (field == null) return false;

      String fromId = fromNode.get(field).textValue();
      String toId = toNode.get(field).textValue();
      return fromId.equals(toId);
    };
  }
}
